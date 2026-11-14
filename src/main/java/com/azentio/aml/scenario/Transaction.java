package com.azentio.aml.scenario;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public record Transaction(
		String txnId,
		String accountNumber,
		Instant txnTime,
		BigDecimal amount,
		Direction direction,
		Channel channel,
		String counterpartyAccount) {

	public Transaction {
		if (txnId == null || txnId.isBlank()) {
			throw new IllegalArgumentException("txnId must not be blank");
		}
		if (accountNumber == null || accountNumber.isBlank()) {
			throw new IllegalArgumentException("accountNumber must not be blank");
		}
		Objects.requireNonNull(txnTime, "txnTime must not be null");
		Objects.requireNonNull(amount, "amount must not be null");
		if (amount.signum() <= 0) {
			throw new IllegalArgumentException("amount must be positive");
		}
		if (amount.scale() > 2) {
			throw new IllegalArgumentException("amount must not have more than two decimal places");
		}
		Objects.requireNonNull(direction, "direction must not be null");
		Objects.requireNonNull(channel, "channel must not be null");
		Objects.requireNonNull(counterpartyAccount, "counterpartyAccount must not be null");
	}
}
