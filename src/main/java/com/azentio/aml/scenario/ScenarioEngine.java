package com.azentio.aml.scenario;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class ScenarioEngine {

	private final ScenarioThresholds thresholds;

	public ScenarioEngine(ScenarioThresholds thresholds) {
		this.thresholds = Objects.requireNonNull(thresholds, "thresholds must not be null");
	}

	public List<ScenarioTrigger> evaluate(Collection<Transaction> accountTransactions, Transaction currentTransaction) {
		Objects.requireNonNull(accountTransactions, "accountTransactions must not be null");
		Objects.requireNonNull(currentTransaction, "currentTransaction must not be null");

		List<ScenarioTrigger> triggered = new ArrayList<>(2);
		evaluateCashStructuring(accountTransactions, currentTransaction).ifPresent(triggered::add);
		evaluateRapidInOut(accountTransactions, currentTransaction).ifPresent(triggered::add);
		return List.copyOf(triggered);
	}

	private Optional<ScenarioTrigger> evaluateCashStructuring(
			Collection<Transaction> accountTransactions,
			Transaction current) {
		ScenarioThresholds.CashStructuring rule = thresholds.cashStructuring();
		if (current.direction() != Direction.CREDIT
				|| current.channel() != Channel.CASH
				|| !isInBand(current.amount(), rule)) {
			return Optional.empty();
		}

		List<Transaction> window = transactionsInWindow(accountTransactions, current, rule.window());
		List<Transaction> qualifying = window.stream()
				.filter(transaction -> transaction.direction() == Direction.CREDIT)
				.filter(transaction -> transaction.channel() == Channel.CASH)
				.filter(transaction -> isInBand(transaction.amount(), rule))
				.toList();

		if (qualifying.size() < rule.minCount()) {
			return Optional.empty();
		}

		BigDecimal total = qualifying.stream()
				.map(Transaction::amount)
				.reduce(BigDecimal.ZERO, BigDecimal::add)
				.setScale(2, RoundingMode.UNNECESSARY);
		return Optional.of(new ScenarioTrigger(
				ScenarioCode.CASH_STRUCTURING,
				new ScenarioDetail.CashStructuring(qualifying.size(), total),
				contributingTxnIds(qualifying)));
	}

	private Optional<ScenarioTrigger> evaluateRapidInOut(
			Collection<Transaction> accountTransactions,
			Transaction current) {
		if (current.direction() != Direction.DEBIT) {
			return Optional.empty();
		}

		ScenarioThresholds.RapidInOut rule = thresholds.rapidInOut();
		List<Transaction> window = transactionsInWindow(accountTransactions, current, rule.window());
		BigDecimal inflow = window.stream()
				.filter(transaction -> transaction.direction() == Direction.CREDIT)
				.map(Transaction::amount)
				.reduce(BigDecimal.ZERO, BigDecimal::add)
				.setScale(2, RoundingMode.UNNECESSARY);
		if (inflow.compareTo(rule.minInflow()) < 0) {
			return Optional.empty();
		}

		BigDecimal outflow = window.stream()
				.filter(transaction -> transaction.direction() == Direction.DEBIT)
				.map(Transaction::amount)
				.reduce(BigDecimal.ZERO, BigDecimal::add)
				.setScale(2, RoundingMode.UNNECESSARY);
		if (outflow.compareTo(inflow.multiply(rule.outflowRatio())) < 0) {
			return Optional.empty();
		}

		BigDecimal ratio = outflow.divide(inflow, MathContext.DECIMAL128);
		return Optional.of(new ScenarioTrigger(
				ScenarioCode.RAPID_IN_OUT,
				new ScenarioDetail.RapidInOut(inflow, outflow, ratio),
				contributingTxnIds(window)));
	}

	private static List<String> contributingTxnIds(List<Transaction> transactions) {
		return transactions.stream()
				.sorted(Comparator.comparing(Transaction::txnTime))
				.map(Transaction::txnId)
				.toList();
	}

	private static List<Transaction> transactionsInWindow(
			Collection<Transaction> accountTransactions,
			Transaction current,
			java.time.Duration window) {
		Instant windowStart = current.txnTime().minus(window);
		List<Transaction> transactions = new ArrayList<>();
		for (Transaction transaction : accountTransactions) {
			if (!transaction.txnId().equals(current.txnId())
					&& transaction.accountNumber().equals(current.accountNumber())
					&& transaction.txnTime().isAfter(windowStart)
					&& !transaction.txnTime().isAfter(current.txnTime())) {
				transactions.add(transaction);
			}
		}
		transactions.add(current);
		return transactions;
	}

	private static boolean isInBand(BigDecimal amount, ScenarioThresholds.CashStructuring rule) {
		return amount.compareTo(rule.bandFloor()) >= 0
				&& amount.compareTo(rule.reportingThreshold()) < 0;
	}
}
