package com.azentio.aml.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

class ScenarioEngineTest {

	private static final Instant NOW = Instant.parse("2026-11-14T07:00:00Z");
	private static final ScenarioEngine ENGINE = new ScenarioEngine(new ScenarioThresholds(
			new ScenarioThresholds.CashStructuring(
					new BigDecimal("800000"), new BigDecimal("1000000"), 3, Duration.ofHours(168)),
			new ScenarioThresholds.RapidInOut(
					new BigDecimal("500000"), new BigDecimal("0.90"), Duration.ofHours(48))));

	@Test
	void cashStructuringTriggersOnThirdInBandDeposit() {
		Transaction first = cashCredit("first", NOW.minusSeconds(7200), "800000");
		Transaction second = cashCredit("second", NOW.minusSeconds(3600), "850000");
		Transaction third = cashCredit("third", NOW, "900000");

		assertTrue(ENGINE.evaluate(List.of(first), second).isEmpty());

		List<ScenarioTrigger> triggered = ENGINE.evaluate(List.of(first, second), third);

		assertEquals(1, triggered.size());
		assertEquals(ScenarioCode.CASH_STRUCTURING, triggered.get(0).scenarioCode());
		assertEquals(new ScenarioDetail.CashStructuring(3, new BigDecimal("2550000.00")),
				triggered.get(0).detail());
		assertEquals(List.of("first", "second", "third"), triggered.get(0).contributingTxnIds());
	}

	@Test
	void cashBandIncludesFloorAndExcludesReportingThreshold() {
		Transaction firstFloorDeposit = cashCredit("floor-1", NOW.minusSeconds(5400), "800000");
		Transaction secondFloorDeposit = cashCredit("floor-2", NOW.minusSeconds(3600), "800000");
		Transaction reportableDeposit = cashCredit("reportable", NOW.minusSeconds(1800), "1000000");
		Transaction current = cashCredit("current", NOW, "800000");

		List<ScenarioTrigger> triggered = ENGINE.evaluate(
				List.of(firstFloorDeposit, secondFloorDeposit, reportableDeposit), current);

		assertEquals(1, triggered.size());
		assertEquals(new ScenarioDetail.CashStructuring(3, new BigDecimal("2400000.00")),
				triggered.get(0).detail());
	}

	@Test
	void cashDepositOlderThan168HoursIsOutsideWindow() {
		Transaction oldDeposit = cashCredit("old", NOW.minus(Duration.ofHours(168)).minusSeconds(1), "850000");
		Transaction recentDeposit = cashCredit("recent", NOW.minusSeconds(60), "850000");
		Transaction current = cashCredit("current", NOW, "850000");

		assertTrue(ENGINE.evaluate(List.of(oldDeposit, recentDeposit), current).isEmpty());
	}

	@Test
	void rapidInOutTriggersAtNinetyPercentButNotEightyNinePercent() {
		Transaction inflow = credit("inflow", NOW.minusSeconds(3600), "1000000");
		Transaction debitAtEightyNinePercent = debit("debit-89", NOW, "890000");
		Transaction debitAtNinetyPercent = debit("debit-90", NOW, "900000");

		assertTrue(ENGINE.evaluate(List.of(inflow), debitAtEightyNinePercent).isEmpty());

		List<ScenarioTrigger> triggered = ENGINE.evaluate(List.of(inflow), debitAtNinetyPercent);

		assertEquals(1, triggered.size());
		assertEquals(ScenarioCode.RAPID_IN_OUT, triggered.get(0).scenarioCode());
		assertEquals(new ScenarioDetail.RapidInOut(
				new BigDecimal("1000000.00"), new BigDecimal("900000.00"), new BigDecimal("0.9")),
				triggered.get(0).detail());
		assertEquals(List.of("inflow", "debit-90"), triggered.get(0).contributingTxnIds());
	}

	@Test
	void rapidInOutDoesNotTriggerBelowMinimumInflow() {
		Transaction inflow = credit("inflow", NOW.minusSeconds(3600), "499999");
		Transaction debit = debit("debit", NOW, "500000");

		assertFalse(ENGINE.evaluate(List.of(inflow), debit).stream()
				.anyMatch(trigger -> trigger.scenarioCode() == ScenarioCode.RAPID_IN_OUT));
	}

	private static Transaction cashCredit(String id, Instant time, String amount) {
		return new Transaction(id, "123456789012", time, new BigDecimal(amount),
				Direction.CREDIT, Channel.CASH, "210987654321");
	}

	private static Transaction credit(String id, Instant time, String amount) {
		return new Transaction(id, "123456789012", time, new BigDecimal(amount),
				Direction.CREDIT, Channel.NEFT, "210987654321");
	}

	private static Transaction debit(String id, Instant time, String amount) {
		return new Transaction(id, "123456789012", time, new BigDecimal(amount),
				Direction.DEBIT, Channel.IMPS, "210987654321");
	}
}
