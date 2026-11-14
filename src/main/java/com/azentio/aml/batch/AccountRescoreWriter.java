package com.azentio.aml.batch;

import com.azentio.aml.alert.AlertSource;
import com.azentio.aml.alert.AlertWriteResult;
import com.azentio.aml.alert.AlertWriter;

import org.springframework.batch.core.StepExecution;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;

public class AccountRescoreWriter implements ItemWriter<AccountRescoreResult> {

	private static final String TRANSACTIONS_READ_KEY = "rescore.transactionsRead";
	private static final String ALERTS_WRITTEN_KEY = "rescore.alertsWritten";

	private final AlertWriter alertWriter;
	private final StepExecution stepExecution;

	public AccountRescoreWriter(AlertWriter alertWriter, StepExecution stepExecution) {
		this.alertWriter = alertWriter;
		this.stepExecution = stepExecution;
	}

	@Override
	public void write(Chunk<? extends AccountRescoreResult> chunk) {
		long transactionsRead = 0;
		long alertsWritten = 0;
		for (AccountRescoreResult result : chunk) {
			transactionsRead += result.transactionsRead();
			for (AlertCandidate candidate : result.alertCandidates()) {
				AlertWriteResult alert = alertWriter.write(
						candidate.transaction(), candidate.trigger(), AlertSource.BATCH);
				if (alert.created()) {
					alertsWritten++;
				}
			}
		}

		var executionContext = stepExecution.getExecutionContext();
		executionContext.putLong(
				TRANSACTIONS_READ_KEY,
				executionContext.getLong(TRANSACTIONS_READ_KEY, 0) + transactionsRead);
		executionContext.putLong(
				ALERTS_WRITTEN_KEY,
				executionContext.getLong(ALERTS_WRITTEN_KEY, 0) + alertsWritten);
	}
}
