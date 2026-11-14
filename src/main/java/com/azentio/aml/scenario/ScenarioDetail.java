package com.azentio.aml.scenario;

import java.math.BigDecimal;

public sealed interface ScenarioDetail
		permits ScenarioDetail.CashStructuring, ScenarioDetail.RapidInOut {

	record CashStructuring(int count, BigDecimal total) implements ScenarioDetail {
	}

	record RapidInOut(BigDecimal inflow, BigDecimal outflow, BigDecimal ratio) implements ScenarioDetail {
	}
}
