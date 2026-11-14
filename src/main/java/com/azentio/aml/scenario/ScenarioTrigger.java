package com.azentio.aml.scenario;

import java.util.Objects;
import java.util.List;

public record ScenarioTrigger(
		ScenarioCode scenarioCode,
		ScenarioDetail detail,
		List<String> contributingTxnIds) {

	public ScenarioTrigger {
		Objects.requireNonNull(scenarioCode, "scenarioCode must not be null");
		Objects.requireNonNull(detail, "detail must not be null");
		contributingTxnIds = List.copyOf(contributingTxnIds);
	}
}
