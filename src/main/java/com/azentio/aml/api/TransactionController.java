package com.azentio.aml.api;

import com.azentio.aml.transaction.TransactionCheckService;

import jakarta.validation.Valid;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

	private final TransactionCheckService transactionCheckService;

	public TransactionController(TransactionCheckService transactionCheckService) {
		this.transactionCheckService = transactionCheckService;
	}

	@PostMapping(value = "/check", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> check(@Valid @RequestBody TransactionCheckRequest request) {
		return ResponseEntity.ok()
				.contentType(MediaType.APPLICATION_JSON)
				.body(transactionCheckService.check(request.toDomain()));
	}
}
