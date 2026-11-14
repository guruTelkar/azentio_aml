package com.azentio.aml.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;

public record AlertDetail(
		UUID id,
		String scenarioCode,
		String accountNumber,
		LocalDate alertDate,
		String triggerTxnId,
		Instant triggerTime,
		String source,
		String status,
		JsonNode detail,
		List<ContributingTransaction> contributingTransactions) {

	public AlertDetail {
		contributingTransactions = List.copyOf(contributingTransactions);
	}
}
