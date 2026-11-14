package com.azentio.aml.batch;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import com.azentio.aml.scenario.ScenarioEngine;
import com.azentio.aml.scenario.ScenarioTrigger;
import com.azentio.aml.scenario.Transaction;

import org.springframework.batch.item.ItemProcessor;

public class AccountRescoreProcessor implements ItemProcessor<AccountTransactionGroup, AccountRescoreResult> {

	private static final ZoneId BANK_ZONE = ZoneId.of("Asia/Kolkata");

	private final ScenarioEngine scenarioEngine;
	private final LocalDate fromDate;
	private final LocalDate toDate;

	public AccountRescoreProcessor(ScenarioEngine scenarioEngine, LocalDate fromDate, LocalDate toDate) {
		this.scenarioEngine = scenarioEngine;
		this.fromDate = fromDate;
		this.toDate = toDate;
	}

	@Override
	public AccountRescoreResult process(AccountTransactionGroup account) {
		List<AlertCandidate> alertCandidates = new ArrayList<>();
		long transactionsRead = account.transactions().size();
		for (Transaction transaction : account.transactions()) {
			LocalDate transactionDate = transaction.txnTime().atZone(BANK_ZONE).toLocalDate();
			if (transactionDate.isBefore(fromDate) || transactionDate.isAfter(toDate)) {
				continue;
			}
			for (ScenarioTrigger trigger : scenarioEngine.evaluate(account.transactions(), transaction)) {
				alertCandidates.add(new AlertCandidate(transaction, trigger));
			}
		}
		return new AccountRescoreResult(transactionsRead, alertCandidates);
	}
}
