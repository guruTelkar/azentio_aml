package com.azentio.aml.alert;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.azentio.aml.scenario.ScenarioCode;
import com.azentio.aml.scenario.ScenarioTrigger;
import com.azentio.aml.scenario.Transaction;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "alerts",
		uniqueConstraints = @UniqueConstraint(
				name = "uq_alert_account_scenario_date",
				columnNames = {"account_number", "scenario_code", "alert_date"}),
		indexes = {
				@Index(name = "idx_alerts_trigger_time", columnList = "trigger_time"),
				@Index(name = "idx_alerts_scenario_source_date", columnList = "scenario_code, source, alert_date")
		})
public class AlertEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "account_number", nullable = false, length = 12)
	private String accountNumber;

	@Enumerated(EnumType.STRING)
	@Column(name = "scenario_code", nullable = false, length = 32)
	private ScenarioCode scenarioCode;

	@Column(name = "alert_date", nullable = false)
	private LocalDate alertDate;

	@Column(name = "trigger_txn_id", nullable = false, length = 64)
	private String triggerTxnId;

	@Column(name = "trigger_time", nullable = false)
	private Instant triggerTime;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private AlertSource source;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private AlertStatus status;

	@Column(name = "scenario_details", nullable = false, columnDefinition = "text")
	private String scenarioDetails;

	@ElementCollection(fetch = FetchType.LAZY)
	@CollectionTable(name = "alert_contributing_transactions",
			joinColumns = @JoinColumn(name = "alert_id", nullable = false))
	@Column(name = "txn_id", nullable = false, length = 64)
	@OrderColumn(name = "position")
	private List<String> contributingTxnIds = new ArrayList<>();

	protected AlertEntity() {
	}

	public AlertEntity(
			Transaction transaction,
			ScenarioTrigger trigger,
			LocalDate alertDate,
			AlertSource source,
			String scenarioDetails) {
		this.accountNumber = transaction.accountNumber();
		this.scenarioCode = trigger.scenarioCode();
		this.alertDate = alertDate;
		this.triggerTxnId = transaction.txnId();
		this.triggerTime = transaction.txnTime();
		this.source = source;
		this.status = AlertStatus.OPEN;
		this.scenarioDetails = scenarioDetails;
		this.contributingTxnIds = new ArrayList<>(trigger.contributingTxnIds());
	}

	public UUID getId() {
		return id;
	}

	public String getAccountNumber() {
		return accountNumber;
	}

	public ScenarioCode getScenarioCode() {
		return scenarioCode;
	}

	public LocalDate getAlertDate() {
		return alertDate;
	}

	public String getTriggerTxnId() {
		return triggerTxnId;
	}

	public Instant getTriggerTime() {
		return triggerTime;
	}

	public AlertSource getSource() {
		return source;
	}

	public AlertStatus getStatus() {
		return status;
	}

	public String getScenarioDetails() {
		return scenarioDetails;
	}

	public List<String> getContributingTxnIds() {
		return List.copyOf(contributingTxnIds);
	}
}
