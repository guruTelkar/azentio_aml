package com.azentio.aml.batch;

import java.util.List;

import com.azentio.aml.scenario.Transaction;

public record AccountTransactionGroup(String accountNumber, List<Transaction> transactions) {

	public AccountTransactionGroup {
		transactions = List.copyOf(transactions);
	}
}
