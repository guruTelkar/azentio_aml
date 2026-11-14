package com.azentio.aml.transaction;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import com.azentio.aml.alert.AlertSource;
import com.azentio.aml.alert.AlertWriter;
import com.azentio.aml.api.TransactionConflictException;
import com.azentio.aml.config.ScenarioProperties;
import com.azentio.aml.scenario.ScenarioEngine;
import com.azentio.aml.scenario.ScenarioTrigger;
import com.azentio.aml.scenario.Transaction;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionCheckService {

	private final TransactionRepository transactions;
	private final AlertWriter alertWriter;
	private final ScenarioEngine scenarioEngine;
	private final ScenarioProperties scenarioProperties;
	private final ObjectMapper objectMapper;

	public TransactionCheckService(
			TransactionRepository transactions,
			AlertWriter alertWriter,
			ScenarioEngine scenarioEngine,
			ScenarioProperties scenarioProperties,
			ObjectMapper objectMapper) {
		this.transactions = transactions;
		this.alertWriter = alertWriter;
		this.scenarioEngine = scenarioEngine;
		this.scenarioProperties = scenarioProperties;
		this.objectMapper = objectMapper;
	}

	@Transactional
	public String check(Transaction transaction) {
		TransactionEntity existing = transactions.findById(transaction.txnId()).orElse(null);
		if (existing != null) {
			if (!existing.hasSamePayload(transaction)) {
				throw new TransactionConflictException(transaction.txnId());
			}
			return existing.getResponseJson();
		}

		TransactionEntity current = transactions.saveAndFlush(new TransactionEntity(transaction));
		Duration lookback = maximumLookback();
		List<Transaction> history = transactions
				.findByAccountNumberAndTxnTimeAfterAndTxnTimeLessThanEqualOrderByTxnTimeAsc(
						transaction.accountNumber(),
						transaction.txnTime().minus(lookback),
						transaction.txnTime())
				.stream()
				.map(TransactionEntity::toDomain)
				.toList();

		List<ScenarioTrigger> triggered = scenarioEngine.evaluate(history, transaction);
		ObjectNode response = objectMapper.createObjectNode();
		response.put("txnId", transaction.txnId());
		ArrayNode triggeredJson = response.putArray("triggered");
		ArrayNode alertIds = response.putArray("alertIds");
		for (ScenarioTrigger trigger : triggered) {
			ObjectNode item = triggeredJson.addObject();
			item.put("scenarioCode", trigger.scenarioCode().name());
			item.set("detail", objectMapper.valueToTree(trigger.detail()));
			UUID alertId = alertWriter.write(transaction, trigger, AlertSource.REALTIME);
			alertIds.add(alertId.toString());
		}

		current.setResponseJson(response.toString());
		transactions.save(current);
		return current.getResponseJson();
	}

	private Duration maximumLookback() {
		Duration cashWindow = scenarioProperties.cashStructuring().window();
		Duration rapidWindow = scenarioProperties.rapidInOut().window();
		return cashWindow.compareTo(rapidWindow) >= 0 ? cashWindow : rapidWindow;
	}

}
