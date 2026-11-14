package com.azentio.aml.alert;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.azentio.aml.api.AlertDetail;
import com.azentio.aml.api.AlertListItem;
import com.azentio.aml.api.BadRequestException;
import com.azentio.aml.api.ContributingTransaction;
import com.azentio.aml.api.PageResponse;
import com.azentio.aml.api.ResourceNotFoundException;
import com.azentio.aml.scenario.ScenarioCode;
import com.azentio.aml.transaction.TransactionEntity;
import com.azentio.aml.transaction.TransactionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AlertQueryService {

	private final AlertRepository alerts;
	private final TransactionRepository transactions;
	private final ObjectMapper objectMapper;

	public AlertQueryService(
			AlertRepository alerts,
			TransactionRepository transactions,
			ObjectMapper objectMapper) {
		this.alerts = alerts;
		this.transactions = transactions;
		this.objectMapper = objectMapper;
	}

	@Transactional(readOnly = true)
	public PageResponse<AlertListItem> search(
			ScenarioCode scenario,
			AlertSource source,
			java.time.LocalDate from,
			java.time.LocalDate to,
			int page,
			int size) {
		if (page < 0 || size < 1 || size > 100) {
			throw new BadRequestException("page must be non-negative and size must be between 1 and 100");
		}
		if (from != null && to != null && from.isAfter(to)) {
			throw new BadRequestException("from must not be after to");
		}

		Sort sort = Sort.by(Sort.Order.desc("triggerTime"), Sort.Order.desc("id"));
		Page<AlertListItem> result = alerts.search(scenario, source, from, to, PageRequest.of(page, size, sort))
				.map(alert -> AlertListItem.from(alert, amountForList(alert)));
		return PageResponse.from(result);
	}

	@Transactional(readOnly = true)
	public AlertDetail find(UUID id) {
		AlertEntity alert = alerts.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("Alert", id));
		List<String> contributingIds = alert.getContributingTxnIds();
		Map<String, TransactionEntity> transactionsById = transactions.findAllByTxnIdIn(contributingIds)
				.stream()
				.collect(Collectors.toMap(TransactionEntity::getTxnId, Function.identity()));
		if (transactionsById.size() != contributingIds.size()) {
			throw new IllegalStateException("An alert references a missing contributing transaction");
		}
		List<ContributingTransaction> contributingTransactions = contributingIds.stream()
				.map(transactionsById::get)
				.map(ContributingTransaction::from)
				.toList();

		return new AlertDetail(
				alert.getId(),
				alert.getScenarioCode().name(),
				com.azentio.aml.api.AccountMasker.mask(alert.getAccountNumber()),
				alert.getAlertDate(),
				alert.getTriggerTxnId(),
				alert.getTriggerTime(),
				alert.getSource().name(),
				alert.getStatus().name(),
				readDetails(alert.getScenarioDetails()),
				contributingTransactions);
	}

	private BigDecimal amountForList(AlertEntity alert) {
		JsonNode details = readDetails(alert.getScenarioDetails());
		String amountField = alert.getScenarioCode() == ScenarioCode.CASH_STRUCTURING ? "total" : "outflow";
		JsonNode amount = details.get(amountField);
		if (amount == null || !amount.isNumber()) {
			throw new IllegalStateException("Alert scenario details are missing " + amountField);
		}
		return amount.decimalValue();
	}

	private JsonNode readDetails(String details) {
		try {
			return objectMapper.readTree(details);
		} catch (JsonProcessingException exception) {
			throw new IllegalStateException("Stored alert scenario details are invalid", exception);
		}
	}
}
