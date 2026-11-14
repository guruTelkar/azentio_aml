package com.azentio.aml.api;

import java.math.BigDecimal;
import java.time.Instant;

import com.azentio.aml.scenario.Channel;
import com.azentio.aml.scenario.Direction;
import com.azentio.aml.transaction.TransactionEntity;

public record ContributingTransaction(
		String txnId,
		Instant txnTime,
		Direction direction,
		Channel channel,
		BigDecimal amount,
		String counterpartyAccount) {

	public static ContributingTransaction from(TransactionEntity transaction) {
		return new ContributingTransaction(
				transaction.getTxnId(),
				transaction.getTxnTime(),
				transaction.getDirection(),
				transaction.getChannel(),
				transaction.getAmount(),
				AccountMasker.mask(transaction.getCounterpartyAccount()));
	}
}
