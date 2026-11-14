package com.azentio.aml.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.math.BigDecimal;

import com.azentio.aml.alert.AlertRepository;
import com.azentio.aml.transaction.TransactionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class TransactionApiIntegrationTest {

	private static final String ACCOUNT = "123456789012";
	private static final String COUNTERPARTY = "987654321098";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private TransactionRepository transactions;

	@Autowired
	private AlertRepository alerts;

	@BeforeEach
	void clearDatabase() {
		alerts.deleteAll();
		transactions.deleteAll();
	}

	@Test
	void identicalReplayReturnsOriginalResponseAndChangedPayloadConflicts() throws Exception {
		postTransaction("cash-1", "2026-11-14T06:00:00Z", "800000");
		postTransaction("cash-2", "2026-11-14T06:30:00Z", "800000");
		String original = postTransaction("cash-3", "2026-11-14T07:00:00Z", "800000")
				.getResponse()
				.getContentAsString();

		String replay = postTransaction("cash-3", "2026-11-14T07:00:00Z", "800000")
				.getResponse()
				.getContentAsString();
		assertEquals(original, replay);
		assertEquals(3, transactions.count());
		assertEquals(1, alerts.count());

		mockMvc.perform(post("/api/transactions/check")
						.contentType(MediaType.APPLICATION_JSON)
						.content(request("cash-3", "2026-11-14T07:00:00Z", "800001").toString()))
				.andExpect(status().isConflict());
		assertEquals(3, transactions.count());
		assertEquals(1, alerts.count());
	}

	@Test
	void createsOneAlertPerAccountScenarioAndIstDateAndMasksAccountsInAlertResponses() throws Exception {
		postTransaction("day-1-a", "2026-11-14T06:00:00Z", "800000");
		postTransaction("day-1-b", "2026-11-14T06:30:00Z", "800000");
		postTransaction("day-1-first-trigger", "2026-11-14T07:00:00Z", "800000");
		postTransaction("day-1-later-trigger", "2026-11-14T07:30:00Z", "800000");
		assertEquals(1, alerts.count());

		postTransaction("day-2-a", "2026-11-15T00:00:00Z", "800000");
		postTransaction("day-2-b", "2026-11-15T00:30:00Z", "800000");
		postTransaction("day-2-first-trigger", "2026-11-15T01:00:00Z", "800000");
		assertEquals(2, alerts.count());

		JsonNode listBody = objectMapper.readTree(mockMvc.perform(get("/api/alerts"))
				.andExpect(status().isOk())
				.andReturn()
				.getResponse()
				.getContentAsString());
		assertEquals(2, listBody.path("content").size());
		assertTrue(listBody.toString().contains("XXXXXXXX9012"));
		assertFalse(listBody.toString().contains(ACCOUNT));

		JsonNode filteredBody = objectMapper.readTree(mockMvc.perform(get("/api/alerts")
						.param("scenario", "CASH_STRUCTURING")
						.param("source", "REALTIME")
						.param("from", "2026-11-14")
						.param("to", "2026-11-14"))
				.andExpect(status().isOk())
				.andReturn()
				.getResponse()
				.getContentAsString());
		assertEquals(1, filteredBody.path("content").size());

		String id = listBody.path("content").get(0).path("id").asText();
		String detailBody = mockMvc.perform(get("/api/alerts/{id}", id))
				.andExpect(status().isOk())
				.andReturn()
				.getResponse()
				.getContentAsString();
		assertTrue(detailBody.contains("XXXXXXXX9012"));
		assertTrue(detailBody.contains("XXXXXXXX1098"));
		assertFalse(detailBody.contains(ACCOUNT));
		assertFalse(detailBody.contains(COUNTERPARTY));
	}

	@Test
	void invalidRequestUsesConsistentBadRequestBody() throws Exception {
		MvcResult result = mockMvc.perform(post("/api/transactions/check")
						.contentType(MediaType.APPLICATION_JSON)
						.content(request("invalid", "2026-11-14T07:00:00Z", "0").toString()))
				.andExpect(status().isBadRequest())
				.andReturn();

		JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
		assertEquals(400, body.path("status").asInt());
		assertEquals("Bad Request", body.path("error").asText());
		assertTrue(body.path("fieldErrors").has("amount"));
		assertEquals(0, transactions.count());
	}

	private MvcResult postTransaction(String txnId, String time, String amount) throws Exception {
		return mockMvc.perform(post("/api/transactions/check")
						.contentType(MediaType.APPLICATION_JSON)
						.content(request(txnId, time, amount).toString()))
				.andExpect(status().isOk())
				.andReturn();
	}

	private ObjectNode request(String txnId, String time, String amount) {
		ObjectNode request = objectMapper.createObjectNode();
		request.put("txnId", txnId);
		request.put("accountNumber", ACCOUNT);
		request.put("txnTime", Instant.parse(time).toString());
		request.put("amount", new BigDecimal(amount));
		request.put("direction", "CREDIT");
		request.put("channel", "CASH");
		request.put("counterpartyAccount", COUNTERPARTY);
		return request;
	}
}
