package com.azentio.aml.api;

public final class AccountMasker {

	private AccountMasker() {
	}

	public static String mask(String accountNumber) {
		if (accountNumber == null || accountNumber.isEmpty()) {
			return accountNumber;
		}
		int visibleDigits = Math.min(4, accountNumber.length());
		return "X".repeat(accountNumber.length() - visibleDigits)
				+ accountNumber.substring(accountNumber.length() - visibleDigits);
	}
}
