package com.azentio.aml.batch;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import javax.sql.DataSource;

import com.azentio.aml.alert.AlertWriter;
import com.azentio.aml.config.ScenarioProperties;
import com.azentio.aml.scenario.Channel;
import com.azentio.aml.scenario.Direction;
import com.azentio.aml.scenario.ScenarioEngine;
import com.azentio.aml.scenario.Transaction;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.partition.support.Partitioner;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.database.JdbcCursorItemReader;
import org.springframework.batch.item.database.builder.JdbcCursorItemReaderBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@EnableConfigurationProperties(BatchProperties.class)
public class RescoreJobConfiguration {

	private static final ZoneId BANK_ZONE = ZoneId.of("Asia/Kolkata");

	@Bean
	Job rescoreJob(
			JobRepository jobRepository,
			@Qualifier("rescorePartitionedStep") Step partitionedStep,
			RescoreJobListener listener) {
		return new JobBuilder("rescoreJob", jobRepository)
				.listener(listener)
				.start(partitionedStep)
				.build();
	}

	@Bean
	Step rescorePartitionedStep(
			JobRepository jobRepository,
			@Qualifier("rescoreWorkerStep") Step workerStep,
			Partitioner accountRangePartitioner,
			@Qualifier("rescoreTaskExecutor") TaskExecutor taskExecutor,
			BatchProperties properties) {
		return new StepBuilder("rescorePartitionedStep", jobRepository)
				.partitioner(workerStep.getName(), accountRangePartitioner)
				.step(workerStep)
				.gridSize(properties.gridSize())
				.taskExecutor(taskExecutor)
				.build();
	}

	@Bean
	Step rescoreWorkerStep(
			JobRepository jobRepository,
			PlatformTransactionManager transactionManager,
			@Qualifier("accountGroupingItemReader") AccountGroupingItemReader reader,
			AccountRescoreProcessor processor,
			AccountRescoreWriter writer,
			BatchProperties properties) {
		return new StepBuilder("rescoreWorkerStep", jobRepository)
				.<AccountTransactionGroup, AccountRescoreResult>chunk(properties.chunkSize(), transactionManager)
				.reader(reader)
				.processor(processor)
				.writer(writer)
				.build();
	}

	@Bean
	Partitioner accountRangePartitioner() {
		return new AccountRangePartitioner();
	}

	@Bean(name = "rescoreTaskExecutor")
	TaskExecutor rescoreTaskExecutor(BatchProperties properties) {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(properties.gridSize());
		executor.setMaxPoolSize(properties.gridSize());
		executor.setQueueCapacity(properties.gridSize());
		executor.setThreadNamePrefix("rescore-");
		executor.setWaitForTasksToCompleteOnShutdown(true);
		return executor;
	}

	@Bean
	@StepScope
	JdbcCursorItemReader<Transaction> transactionCursorItemReader(
			DataSource dataSource,
			BatchProperties properties,
			ScenarioProperties scenarioProperties,
			@Value("#{stepExecutionContext['accountFrom']}") String accountFrom,
			@Value("#{stepExecutionContext['accountTo']}") String accountTo,
			@Value("#{jobParameters['fromDate']}") String fromDate,
			@Value("#{jobParameters['toDate']}") String toDate) {
		Instant queryStart = LocalDate.parse(fromDate).atStartOfDay(BANK_ZONE).toInstant()
				.minus(maximumLookback(scenarioProperties));
		Instant queryEnd = LocalDate.parse(toDate).plusDays(1).atStartOfDay(BANK_ZONE).toInstant();
		String sql = """
				SELECT txn_id, account_number, txn_time, amount, direction, channel, counterparty_account
				FROM transactions
				WHERE account_number >= ? AND account_number <= ?
				  AND txn_time >= ? AND txn_time < ?
				ORDER BY account_number, txn_time
				""";
		return new JdbcCursorItemReaderBuilder<Transaction>()
				.name("transactionCursorItemReader")
				.dataSource(dataSource)
				.sql(sql)
				.queryArguments(
						accountFrom,
						accountTo,
						Timestamp.from(queryStart),
						Timestamp.from(queryEnd))
				.fetchSize(properties.fetchSize())
				.rowMapper((resultSet, rowNumber) -> new Transaction(
						resultSet.getString("txn_id"),
						resultSet.getString("account_number"),
						resultSet.getObject("txn_time", java.time.OffsetDateTime.class).toInstant(),
						resultSet.getBigDecimal("amount"),
						Direction.valueOf(resultSet.getString("direction")),
						Channel.valueOf(resultSet.getString("channel")),
						resultSet.getString("counterparty_account")))
				.build();
	}

	@Bean
	@StepScope
	AccountGroupingItemReader accountGroupingItemReader(
			@Qualifier("transactionCursorItemReader") JdbcCursorItemReader<Transaction> reader,
			ObjectMapper objectMapper) {
		return new AccountGroupingItemReader(reader, objectMapper);
	}

	@Bean
	@StepScope
	AccountRescoreProcessor accountRescoreProcessor(
			ScenarioEngine scenarioEngine,
			@Value("#{jobParameters['fromDate']}") String fromDate,
			@Value("#{jobParameters['toDate']}") String toDate) {
		return new AccountRescoreProcessor(scenarioEngine, LocalDate.parse(fromDate), LocalDate.parse(toDate));
	}

	@Bean
	@StepScope
	AccountRescoreWriter accountRescoreWriter(
			AlertWriter alertWriter,
			@Value("#{stepExecution}") StepExecution stepExecution) {
		return new AccountRescoreWriter(alertWriter, stepExecution);
	}

	@Bean
	RescoreJobListener rescoreJobListener() {
		return new RescoreJobListener();
	}

	private static Duration maximumLookback(ScenarioProperties properties) {
		Duration cashWindow = properties.cashStructuring().window();
		Duration rapidWindow = properties.rapidInOut().window();
		return cashWindow.compareTo(rapidWindow) >= 0 ? cashWindow : rapidWindow;
	}
}
