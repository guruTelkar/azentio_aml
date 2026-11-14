package com.azentio.aml.batch;

import java.util.ArrayList;
import java.util.List;

import com.azentio.aml.scenario.Transaction;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemStreamException;
import org.springframework.batch.item.ItemStreamReader;

public class AccountGroupingItemReader implements ItemStreamReader<AccountTransactionGroup> {

	private static final String LOOKAHEAD_KEY = "accountGrouping.lookAhead";

	private final ItemStreamReader<Transaction> delegate;
	private final ObjectMapper objectMapper;
	private Transaction lookAhead;

	public AccountGroupingItemReader(ItemStreamReader<Transaction> delegate, ObjectMapper objectMapper) {
		this.delegate = delegate;
		this.objectMapper = objectMapper;
	}

	@Override
	public void open(ExecutionContext executionContext) throws ItemStreamException {
		delegate.open(executionContext);
		if (executionContext.containsKey(LOOKAHEAD_KEY)) {
			try {
				lookAhead = objectMapper.readValue(executionContext.getString(LOOKAHEAD_KEY), Transaction.class);
			} catch (JsonProcessingException exception) {
				throw new ItemStreamException("Could not restore the next account transaction", exception);
			}
		}
	}

	@Override
	public AccountTransactionGroup read() throws Exception {
		Transaction first = lookAhead;
		lookAhead = null;
		if (first == null) {
			first = delegate.read();
		}
		if (first == null) {
			return null;
		}

		List<Transaction> transactions = new ArrayList<>();
		transactions.add(first);
		Transaction next;
		while ((next = delegate.read()) != null) {
			if (!next.accountNumber().equals(first.accountNumber())) {
				lookAhead = next;
				break;
			}
			transactions.add(next);
		}
		return new AccountTransactionGroup(first.accountNumber(), transactions);
	}

	@Override
	public void update(ExecutionContext executionContext) throws ItemStreamException {
		delegate.update(executionContext);
		if (lookAhead == null) {
			executionContext.remove(LOOKAHEAD_KEY);
			return;
		}
		try {
			executionContext.putString(LOOKAHEAD_KEY, objectMapper.writeValueAsString(lookAhead));
		} catch (JsonProcessingException exception) {
			throw new ItemStreamException("Could not save the next account transaction", exception);
		}
	}

	@Override
	public void close() throws ItemStreamException {
		delegate.close();
	}
}
