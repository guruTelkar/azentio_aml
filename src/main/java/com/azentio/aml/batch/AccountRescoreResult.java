package com.azentio.aml.batch;

import java.util.List;

public record AccountRescoreResult(long transactionsRead, List<AlertCandidate> alertCandidates) {

	public AccountRescoreResult {
		alertCandidates = List.copyOf(alertCandidates);
	}
}
