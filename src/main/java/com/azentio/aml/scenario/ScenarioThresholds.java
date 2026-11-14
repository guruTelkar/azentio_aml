package com.azentio.aml.scenario;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Objects;

public record ScenarioThresholds(CashStructuring cashStructuring, RapidInOut rapidInOut) {

	public ScenarioThresholds {
		Objects.requireNonNull(cashStructuring, "cashStructuring must not be null");
		Objects.requireNonNull(rapidInOut, "rapidInOut must not be null");
	}

	public record CashStructuring(
			BigDecimal bandFloor,
			BigDecimal reportingThreshold,
			int minCount,
			Duration window) {

		public CashStructuring {
			requirePositive(bandFloor, "bandFloor");
			requirePositive(reportingThreshold, "reportingThreshold");
			if (bandFloor.compareTo(reportingThreshold) >= 0) {
				throw new IllegalArgumentException("bandFloor must be less than reportingThreshold");
			}
			if (minCount < 1) {
				throw new IllegalArgumentException("minCount must be positive");
			}
			requirePositive(window, "cash structuring window");
		}
	}

	public record RapidInOut(
			BigDecimal minInflow,
			BigDecimal outflowRatio,
			Duration window) {

		public RapidInOut {
			requirePositive(minInflow, "minInflow");
			requirePositive(outflowRatio, "outflowRatio");
			requirePositive(window, "rapid in-out window");
		}
	}

	private static void requirePositive(BigDecimal value, String name) {
		Objects.requireNonNull(value, name + " must not be null");
		if (value.signum() <= 0) {
			throw new IllegalArgumentException(name + " must be positive");
		}
	}

	private static void requirePositive(Duration value, String name) {
		Objects.requireNonNull(value, name + " must not be null");
		if (value.isZero() || value.isNegative()) {
			throw new IllegalArgumentException(name + " must be positive");
		}
	}
}
