package com.azentio.aml.transaction;

import java.math.BigDecimal;
import java.time.Instant;

import com.azentio.aml.scenario.Channel;
import com.azentio.aml.scenario.Direction;
import com.azentio.aml.scenario.Transaction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(name = "transactions", indexes = {
		@Index(name = "idx_transactions_account_time", columnList = "account_number, txn_time"),
		@Index(name = "idx_transactions_time_account", columnList = "txn_time, account_number")
})
public class TransactionEntity {

	@Id
	@Column(name = "txn_id", nullable = false, length = 64)
	private String txnId;

	@Column(name = "account_number", nullable = false, length = 12)
	private String accountNumber;

	@Column(name = "txn_time", nullable = false)
	private Instant txnTime;

	@Column(nullable = false, precision = 19, scale = 2)
	private BigDecimal amount;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 6)
	private Direction direction;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 8)
	private Channel channel;

	@Column(name = "counterparty_account", nullable = false, length = 64)
	private String counterpartyAccount;

	@Column(name = "response_json", columnDefinition = "text")
	private String responseJson;

	protected TransactionEntity() {
	}

	public TransactionEntity(Transaction transaction) {
		this.txnId = transaction.txnId();
		this.accountNumber = transaction.accountNumber();
		this.txnTime = transaction.txnTime();
		this.amount = transaction.amount();
		this.direction = transaction.direction();
		this.channel = transaction.channel();
		this.counterpartyAccount = transaction.counterpartyAccount();
	}

	public Transaction toDomain() {
		return new Transaction(txnId, accountNumber, txnTime, amount, direction, channel, counterpartyAccount);
	}

	public boolean hasSamePayload(Transaction transaction) {
		return accountNumber.equals(transaction.accountNumber())
				&& txnTime.equals(transaction.txnTime())
				&& amount.compareTo(transaction.amount()) == 0
				&& direction == transaction.direction()
				&& channel == transaction.channel()
				&& counterpartyAccount.equals(transaction.counterpartyAccount());
	}

	public String getTxnId() {
		return txnId;
	}

	public String getAccountNumber() {
		return accountNumber;
	}

	public Instant getTxnTime() {
		return txnTime;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public Direction getDirection() {
		return direction;
	}

	public Channel getChannel() {
		return channel;
	}

	public String getCounterpartyAccount() {
		return counterpartyAccount;
	}

	public String getResponseJson() {
		return responseJson;
	}

	public void setResponseJson(String responseJson) {
		this.responseJson = responseJson;
	}
}
