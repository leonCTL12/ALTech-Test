package dev.wallet.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.wallet.domain.LedgerEntry;
import dev.wallet.repository.LedgerEntryRepository;
import dev.wallet.repository.WalletRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class WalletTransactionHistoryControllerTest {

	private static final ObjectMapper JSON = new ObjectMapper();

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private WalletRepository wallets;

	@Autowired
	private LedgerEntryRepository ledgerEntries;

	@Test
	void freshPlayerWalletHasEmptyHistory() throws Exception {
		long playerId = createPlayer();

		mockMvc.perform(get("/players/{playerId}/wallet/transactions", playerId))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.entries").isArray())
				.andExpect(jsonPath("$.entries").isEmpty())
				.andExpect(jsonPath("$.nextAfter").value(nullValue()));
	}

	@Test
	void unknownPlayerReturns404() throws Exception {
		mockMvc.perform(get("/players/{playerId}/wallet/transactions", 999_999L))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("player_not_found"));
	}

	@Test
	void historyReturnsEntriesNewestFirstWithTheirReasonsAndAmounts() throws Exception {
		long playerId = createPlayer();
		credit(playerId, "10.00", "MISSION_REWARD", "Completed level 3");
		debit(playerId, "4.00", "PURCHASE", "Starter pack");
		credit(playerId, "5.50", "MISSION_REWARD", "Daily bonus");

		mockMvc.perform(get("/players/{playerId}/wallet/transactions", playerId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.entries.length()").value(3))
				.andExpect(jsonPath("$.nextAfter").value(nullValue()))
				.andExpect(jsonPath("$.entries[0].amount").value("5.50"))
				.andExpect(jsonPath("$.entries[0].direction").value("CREDIT"))
				.andExpect(jsonPath("$.entries[0].reason.reasonKind").value("MISSION_REWARD"))
				.andExpect(jsonPath("$.entries[0].reason.description").value("Daily bonus"))
				.andExpect(jsonPath("$.entries[0].createdAt").isNotEmpty())
				.andExpect(jsonPath("$.entries[1].amount").value("4.00"))
				.andExpect(jsonPath("$.entries[1].direction").value("DEBIT"))
				.andExpect(jsonPath("$.entries[1].reason.reasonKind").value("PURCHASE"))
				.andExpect(jsonPath("$.entries[1].reason.description").value("Starter pack"))
				.andExpect(jsonPath("$.entries[2].amount").value("10.00"))
				.andExpect(jsonPath("$.entries[2].direction").value("CREDIT"))
				.andExpect(jsonPath("$.entries[2].reason.reasonKind").value("MISSION_REWARD"))
				.andExpect(jsonPath("$.entries[2].reason.description").value("Completed level 3"));
	}

	private void credit(long playerId, String amount, String reasonKind, String description) throws Exception {
		mockMvc.perform(post("/players/{playerId}/wallet/credit", playerId)
						.contentType(MediaType.APPLICATION_JSON)
						.content(changeBody(amount, UUID.randomUUID().toString(), reasonKind, description)))
				.andExpect(status().isOk());
	}

	private void debit(long playerId, String amount, String reasonKind, String description) throws Exception {
		mockMvc.perform(post("/players/{playerId}/wallet/debit", playerId)
						.contentType(MediaType.APPLICATION_JSON)
						.content(changeBody(amount, UUID.randomUUID().toString(), reasonKind, description)))
				.andExpect(status().isOk());
	}

	private String changeBody(String amount, String requestId, String reasonKind, String description) {
		return "{"
				+ "\"amount\":\"" + amount + "\","
				+ "\"requestId\":\"" + requestId + "\","
				+ "\"reason\":{\"reasonKind\":\"" + reasonKind + "\",\"description\":\"" + description + "\"}"
				+ "}";
	}

	@Test
	void limitCapsThePageSize() throws Exception {
		long playerId = createPlayer();
		credit(playerId, "10.00", "MISSION_REWARD", "Level 1");
		credit(playerId, "20.00", "MISSION_REWARD", "Level 2");
		credit(playerId, "30.00", "MISSION_REWARD", "Level 3");

		mockMvc.perform(get("/players/{playerId}/wallet/transactions", playerId)
						.param("limit", "2"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.entries.length()").value(2))
				.andExpect(jsonPath("$.entries[0].amount").value("30.00"))
				.andExpect(jsonPath("$.entries[1].amount").value("20.00"))
				.andExpect(jsonPath("$.nextAfter").isNumber());
	}

	@Test
	void afterResumesFromTheGivenEntryId() throws Exception {
		long playerId = createPlayer();
		credit(playerId, "10.00", "MISSION_REWARD", "Level 1");
		credit(playerId, "20.00", "MISSION_REWARD", "Level 2");
		credit(playerId, "30.00", "MISSION_REWARD", "Level 3");
		long secondNewestId = entryIds(playerId).get(1);

		mockMvc.perform(get("/players/{playerId}/wallet/transactions", playerId)
						.param("after", String.valueOf(secondNewestId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.entries.length()").value(1))
				.andExpect(jsonPath("$.entries[0].amount").value("10.00"))
				.andExpect(jsonPath("$.nextAfter").value(nullValue()));
	}

	private List<Long> entryIds(long playerId) {
		List<LedgerEntry> ascending = ledgerEntries
				.findByWalletIdOrderByIdAsc(wallets.findByPlayerId(playerId).orElseThrow().getId());
		List<Long> ids = new ArrayList<>();
		for (int i = ascending.size() - 1; i >= 0; i--) {
			ids.add(ascending.get(i).getId());
		}
		return ids;
	}

	@Test
	void pagingThroughMixedHistoryReturnsEveryEntryExactlyOnce() throws Exception {
		long playerId = createPlayer();
		credit(playerId, "10.00", "MISSION_REWARD", "Level 1");
		debit(playerId, "2.00", "PURCHASE", "Skin A");
		credit(playerId, "5.00", "MISSION_REWARD", "Level 2");
		debit(playerId, "3.00", "PURCHASE", "Skin B");
		credit(playerId, "1.00", "ADMIN", "Bonus");
		debit(playerId, "4.00", "PURCHASE", "Skin C");
		List<Long> allDescending = entryIds(playerId);

		List<Long> seen = new ArrayList<>();
		Long cursor = null;
		for (int page = 0; page < 100; page++) {
			MockHttpServletRequestBuilder request = get("/players/{playerId}/wallet/transactions", playerId).param("limit", "2");
			if (cursor != null) {
				request.param("after", String.valueOf(cursor));
			}
			JsonNode body = JSON.readTree(mockMvc.perform(request)
					.andExpect(status().isOk())
					.andReturn().getResponse().getContentAsString());
			for (JsonNode entry : body.get("entries")) {
				seen.add(entry.get("id").asLong());
			}
			if (body.get("nextAfter").isNull()) {
				break;
			}
			cursor = body.get("nextAfter").asLong();
		}

		assertThat(seen).containsExactlyElementsOf(allDescending);
	}

	@Test
	void invalidLimitOrCursorReturns400() throws Exception {
		long playerId = createPlayer();
		credit(playerId, "10.00", "MISSION_REWARD", "Level 1");

		for (String limit : new String[]{"0", "-1", "101", "abc"}) {
			mockMvc.perform(get("/players/{playerId}/wallet/transactions", playerId).param("limit", limit))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.code").value("invalid_limit"));
		}
		for (String after : new String[]{"0", "-5", "abc"}) {
			mockMvc.perform(get("/players/{playerId}/wallet/transactions", playerId).param("after", after))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.code").value("invalid_cursor"));
		}
	}

	private long createPlayer() throws Exception {
		String body = mockMvc.perform(post("/players"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return JSON.readTree(body).get("playerId").asLong();
	}
}