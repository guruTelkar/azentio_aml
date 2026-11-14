package com.azentio.aml.batch;

import java.time.LocalDate;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;

public class RescoreJobListener implements JobExecutionListener {

	private static final Logger LOGGER = LoggerFactory.getLogger(RescoreJobListener.class);
	private static final String START_NANOS_KEY = "rescore.startedAtNanos";

	@Override
	public void beforeJob(JobExecution jobExecution) {
		String fromDate = jobExecution.getJobParameters().getString("fromDate");
		String toDate = jobExecution.getJobParameters().getString("toDate");
		if (fromDate == null || toDate == null) {
			throw new IllegalArgumentException("fromDate and toDate job parameters are required");
		}
		LocalDate from = LocalDate.parse(fromDate);
		LocalDate to = LocalDate.parse(toDate);
		if (from.isAfter(to)) {
			throw new IllegalArgumentException("fromDate must not be after toDate");
		}
		jobExecution.getExecutionContext().putLong(START_NANOS_KEY, System.nanoTime());
	}

	@Override
	public void afterJob(JobExecution jobExecution) {
		long transactionsRead = 0;
		long alertsWritten = 0;
		for (var stepExecution : jobExecution.getStepExecutions()) {
			var context = stepExecution.getExecutionContext();
			transactionsRead += context.getLong("rescore.transactionsRead", 0);
			alertsWritten += context.getLong("rescore.alertsWritten", 0);
		}
		long startedAtNanos = jobExecution.getExecutionContext().getLong(START_NANOS_KEY, System.nanoTime());
		long durationMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos);
		LOGGER.info(
				"Rescore finished: status={}, transactionsRead={}, alertsWritten={}, durationMs={}",
				jobExecution.getStatus(),
				transactionsRead,
				alertsWritten,
				durationMillis);
	}
}
