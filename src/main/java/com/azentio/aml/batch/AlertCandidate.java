package com.azentio.aml.batch;

import com.azentio.aml.scenario.ScenarioTrigger;
import com.azentio.aml.scenario.Transaction;

public record AlertCandidate(Transaction transaction, ScenarioTrigger trigger) {
}
