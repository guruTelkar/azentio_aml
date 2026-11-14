package com.azentio.aml.config;

import java.math.BigDecimal;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.azentio.aml.scenario.ScenarioThresholds;

@ConfigurationProperties(prefix = "aml.scenarios")
public record ScenarioProperties(
		CashStructuring cashStructuring,
		RapidInOut rapidInOut) {

	public ScenarioThresholds toThresholds() {
		return new ScenarioThresholds(
				new ScenarioThresholds.CashStructuring(
						cashStructuring.bandFloor(),
						cashStructuring.reportingThreshold(),
						cashStructuring.minCount(),
						cashStructuring.window()),
				new ScenarioThresholds.RapidInOut(
						rapidInOut.minInflow(),
						rapidInOut.outflowRatio(),
						rapidInOut.window()));
	}

	public record CashStructuring(
			BigDecimal bandFloor,
			BigDecimal reportingThreshold,
			int minCount,
			Duration window) {
	}

	public record RapidInOut(
			BigDecimal minInflow,
			BigDecimal outflowRatio,
			Duration window) {
	}
}
