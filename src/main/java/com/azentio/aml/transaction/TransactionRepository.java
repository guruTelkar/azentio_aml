package com.azentio.aml.transaction;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionRepository extends JpaRepository<TransactionEntity, String> {

	List<TransactionEntity> findByAccountNumberAndTxnTimeAfterAndTxnTimeLessThanEqualOrderByTxnTimeAsc(
			String accountNumber,
			Instant txnTimeAfter,
			Instant txnTimeLessThanEqual);

	List<TransactionEntity> findAllByTxnIdIn(Collection<String> txnIds);
}
