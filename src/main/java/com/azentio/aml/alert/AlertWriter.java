package com.azentio.aml.alert;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import com.azentio.aml.scenario.ScenarioTrigger;
import com.azentio.aml.scenario.Transaction;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.stereotype.Service;

@Service
public class AlertWriter {

	private static final ZoneId BANK_ZONE = ZoneId.of("Asia/Kolkata");

	private final AlertRepository alerts;
	private final ObjectMapper objectMapper;

	public AlertWriter(AlertRepository alerts, ObjectMapper objectMapper) {
		this.alerts = alerts;
		this.objectMapper = objectMapper;
	}

	public UUID write(Transaction transaction, ScenarioTrigger trigger, AlertSource source) {
		LocalDate alertDate = transaction.txnTime().atZone(BANK_ZONE).toLocalDate();
		return alerts.findByAccountNumberAndScenarioCodeAndAlertDate(
						transaction.accountNumber(), trigger.scenarioCode(), alertDate)
				.map(AlertEntity::getId)
				.orElseGet(() -> create(transaction, trigger, alertDate, source));
	}

	private UUID create(
			Transaction transaction,
			ScenarioTrigger trigger,
			LocalDate alertDate,
			AlertSource source) {
		String details = objectMapper.valueToTree(trigger.detail()).toString();
		AlertEntity alert = new AlertEntity(transaction, trigger, alertDate, source, details);
		return alerts.saveAndFlush(alert).getId();
	}
}
