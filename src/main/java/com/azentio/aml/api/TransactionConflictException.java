package com.azentio.aml.api;

public class TransactionConflictException extends RuntimeException {

	public TransactionConflictException(String txnId) {
		super("txnId has already been used with a different payload: " + txnId);
	}
}
