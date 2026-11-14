package com.azentio.aml.batch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.azentio.aml.alert.AlertRepository;
import com.azentio.aml.transaction.TransactionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class RescoreJobIntegrationTest {

	private static final String CASH_ACCOUNT = "123456789012";
	private static final String RAPID_ACCOUNT = "823456789012";
	private static final String COUNTERPARTY = "987654321098";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private AlertRepository alerts;

	@Autowired
	private TransactionRepository transactions;

	@Autowired
	private JobLauncher jobLauncher;

	@Autowired
	@Qualifier("rescoreJob")
	private Job rescoreJob;

	@BeforeEach
	void clearDatabase() {
		alerts.deleteAll();
		transactions.deleteAll();
	}

	@Test
	void partitionedRescoreMatchesRealtimeAlertsAndRerunDoesNotDuplicateThem() throws Exception {
		postTransaction("cash-1", CASH_ACCOUNT, "2026-11-13T06:00:00Z", "800000", "CREDIT", "CASH");
		postTransaction("cash-2", CASH_ACCOUNT, "2026-11-13T06:30:00Z", "800000", "CREDIT", "CASH");
		postTransaction("cash-3", CASH_ACCOUNT, "2026-11-14T07:00:00Z", "800000", "CREDIT", "CASH");
		postTransaction("rapid-in", RAPID_ACCOUNT, "2026-11-14T06:00:00Z", "500000", "CREDIT", "UPI");
		postTransaction("rapid-out", RAPID_ACCOUNT, "2026-11-14T07:00:00Z", "450000", "DEBIT", "UPI");

		Set<AlertSnapshot> realtimeAlerts = loadAlerts();
		assertEquals(2, realtimeAlerts.size());
		alerts.deleteAll();

		JobExecution firstRun = launchRun(1);
		assertEquals(BatchStatus.COMPLETED, firstRun.getStatus());
		assertEquals(realtimeAlerts, loadAlerts());
		assertEquals(2, jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM alerts WHERE source = 'BATCH'", Integer.class));
		assertEquals(4, workerPartitions(firstRun));
		assertEquals(5, metricTotal(firstRun, "rescore.transactionsRead"));
		assertEquals(2, metricTotal(firstRun, "rescore.alertsWritten"));

		JobExecution rerun = launchRun(2);
		assertEquals(BatchStatus.COMPLETED, rerun.getStatus());
		assertEquals(realtimeAlerts, loadAlerts());
		assertEquals(0, metricTotal(rerun, "rescore.alertsWritten"));
	}

	private JobExecution launchRun(long runId) throws Exception {
		return jobLauncher.run(
				rescoreJob,
				new JobParametersBuilder()
						.addString("fromDate", "2026-11-14")
						.addString("toDate", "2026-11-14")
						.addLong("run.id", runId)
						.toJobParameters());
	}

	private Set<AlertSnapshot> loadAlerts() {
		List<AlertRow> alertRows = jdbcTemplate.query(
				"SELECT id, account_number, scenario_code, alert_date, trigger_txn_id, scenario_details FROM alerts",
				(resultSet, rowNumber) -> new AlertRow(
						resultSet.getObject("id", UUID.class),
						resultSet.getString("account_number"),
						resultSet.getString("scenario_code"),
						resultSet.getDate("alert_date").toLocalDate(),
						resultSet.getString("trigger_txn_id"),
						resultSet.getString("scenario_details")));
		Set<AlertSnapshot> snapshots = new HashSet<>();
		for (AlertRow alert : alertRows) {
			List<String> contributingTxnIds = jdbcTemplate.query(
					"SELECT txn_id FROM alert_contributing_transactions WHERE alert_id = ? ORDER BY position",
					(resultSet, rowNumber) -> resultSet.getString("txn_id"),
					alert.id());
			snapshots.add(new AlertSnapshot(
					alert.accountNumber(),
					alert.scenarioCode(),
					alert.alertDate(),
					alert.triggerTxnId(),
					alert.scenarioDetails(),
					List.copyOf(contributingTxnIds)));
		}
		return Set.copyOf(snapshots);
	}

	private static long metricTotal(JobExecution execution, String key) {
		return execution.getStepExecutions().stream()
				.mapToLong(step -> step.getExecutionContext().getLong(key, 0))
				.sum();
	}

	private static long workerPartitions(JobExecution execution) {
		return execution.getStepExecutions().stream()
				.filter(step -> step.getStepName().startsWith("rescoreWorkerStep"))
				.count();
	}

	private void postTransaction(
			String txnId,
			String accountNumber,
			String time,
			String amount,
			String direction,
			String channel) throws Exception {
		ObjectNode request = objectMapper.createObjectNode();
		request.put("txnId", txnId);
		request.put("accountNumber", accountNumber);
		request.put("txnTime", Instant.parse(time).toString());
		request.put("amount", new BigDecimal(amount));
		request.put("direction", direction);
		request.put("channel", channel);
		request.put("counterpartyAccount", COUNTERPARTY);

		mockMvc.perform(post("/api/transactions/check")
						.contentType(MediaType.APPLICATION_JSON)
						.content(request.toString()))
				.andExpect(status().isOk());
	}

	private record AlertRow(
			UUID id,
			String accountNumber,
			String scenarioCode,
			LocalDate alertDate,
			String triggerTxnId,
			String scenarioDetails) {
	}

	private record AlertSnapshot(
			String accountNumber,
			String scenarioCode,
			LocalDate alertDate,
			String triggerTxnId,
			String scenarioDetails,
			List<String> contributingTxnIds) {
	}
}
