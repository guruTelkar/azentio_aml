package com.azentio.aml.generator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SyntheticTransactionGeneratorTest {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void writesRequestedRowsAndGroundTruthForPlantedPatterns(@TempDir Path directory) throws Exception {
		Path csv = directory.resolve("transactions.csv");
		Path truth = directory.resolve("truth.json");

		SyntheticTransactionGenerator.generate(
				20, 42, Instant.parse("2026-11-14T07:00:00Z"), csv, truth);

		List<String> lines = Files.readAllLines(csv);
		assertEquals(21, lines.size());
		assertTrue(lines.get(0).startsWith("txnId,accountNumber,txnTime,amount"));

		JsonNode groundTruth = objectMapper.readTree(truth.toFile());
		assertEquals("900000000001",
				groundTruth.path("CASH_STRUCTURING").path("plantedAccounts").get(0).asText());
		assertEquals("900000000002",
				groundTruth.path("CASH_STRUCTURING").path("nearMissAccounts").get(0).asText());
		assertEquals("900000000003",
				groundTruth.path("RAPID_IN_OUT").path("plantedAccounts").get(0).asText());
		assertEquals("900000000004",
				groundTruth.path("RAPID_IN_OUT").path("nearMissAccounts").get(0).asText());
	}
}
