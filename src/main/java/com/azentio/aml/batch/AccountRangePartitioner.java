package com.azentio.aml.batch;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.springframework.batch.core.partition.support.Partitioner;
import org.springframework.batch.item.ExecutionContext;

public class AccountRangePartitioner implements Partitioner {

	private static final long ACCOUNT_SPACE = 1_000_000_000_000L;

	@Override
	public Map<String, ExecutionContext> partition(int gridSize) {
		if (gridSize < 1 || gridSize > 64) {
			throw new IllegalArgumentException("gridSize must be between 1 and 64");
		}

		Map<String, ExecutionContext> partitions = new LinkedHashMap<>();
		for (int partition = 0; partition < gridSize; partition++) {
			long lowerBound = ACCOUNT_SPACE * partition / gridSize;
			long upperBound = ACCOUNT_SPACE * (partition + 1L) / gridSize - 1;
			ExecutionContext context = new ExecutionContext();
			context.putString("accountFrom", accountNumber(lowerBound));
			context.putString("accountTo", accountNumber(upperBound));
			partitions.put("partition" + partition, context);
		}
		return partitions;
	}

	private static String accountNumber(long value) {
		return String.format(Locale.ROOT, "%012d", value);
	}
}
