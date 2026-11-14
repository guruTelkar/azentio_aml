package com.azentio.aml.api;

import java.math.BigDecimal;
import java.time.Instant;

import com.azentio.aml.scenario.Channel;
import com.azentio.aml.scenario.Direction;
import com.azentio.aml.scenario.Transaction;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record TransactionCheckRequest(
		@NotBlank @Size(max = 64) String txnId,
		@NotBlank @Pattern(regexp = "\\d{12}") String accountNumber,
		@NotNull Instant txnTime,
		@NotNull @DecimalMin(value = "0.0", inclusive = false) @Digits(integer = 17, fraction = 2) BigDecimal amount,
		@NotNull Direction direction,
		@NotNull Channel channel,
		@NotBlank @Size(max = 64) String counterpartyAccount) {

	public Transaction toDomain() {
		return new Transaction(txnId, accountNumber, txnTime, amount, direction, channel, counterpartyAccount);
	}
}
