package com.azentio.aml.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.azentio.aml.alert.AlertEntity;

public record AlertListItem(
		UUID id,
		String scenarioCode,
		String accountNumber,
		LocalDate alertDate,
		Instant triggerTime,
		String source,
		BigDecimal totalAmount,
		String status) {

	public static AlertListItem from(AlertEntity alert, BigDecimal totalAmount) {
		return new AlertListItem(
				alert.getId(),
				alert.getScenarioCode().name(),
				AccountMasker.mask(alert.getAccountNumber()),
				alert.getAlertDate(),
				alert.getTriggerTime(),
				alert.getSource().name(),
				totalAmount,
				alert.getStatus().name());
	}
}
