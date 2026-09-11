package dev.wallet.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.wallet.domain.Direction;
import dev.wallet.domain.LedgerEntry;
import dev.wallet.domain.ReasonKind;
import dev.wallet.repository.LedgerEntryRepository;
import dev.wallet.repository.PlayerRepository;
import dev.wallet.repository.WalletRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class WalletCreditControllerTest {

	private static final ObjectMapper JSON = new ObjectMapper();

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private PlayerRepository players;

	@Autowired
	private WalletRepository wallets;

	@Autowired
	private LedgerEntryRepository ledgerEntries;

	@Test
	void creditIncreasesBalanceAndAppendsOneLedgerEntryWithTheReason() throws Exception {
		long playerId = createPlayer();
		long walletId = wallets.findByPlayerId(playerId).orElseThrow().getId();
		String requestId = UUID.randomUUID().toString();

		mockMvc.perform(post("/players/{playerId}/wallet/credit", playerId)
						.contentType(MediaType.APPLICATION_JSON)
						.content(creditBody("10.00", requestId, "MISSION_REWARD", "Completed level 3")))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.balance").value("10.00"));

		assertThat(balanceOf(playerId)).isEqualTo(1000L);

		List<LedgerEntry> entries = ledgerEntries.findByWalletIdOrderByIdAsc(walletId);
		assertThat(entries).hasSize(1);
		LedgerEntry entry = entries.get(0);
		assertThat(entry.getAmount()).isEqualTo(1000L);
		assertThat(entry.getRequestId()).isEqualTo(requestId);
		assertThat(entry.getDirection()).isEqualTo(Direction.CREDIT);
		assertThat(entry.getWallet().getId()).isEqualTo(walletId);
		assertThat(entry.getReason().getKind()).isEqualTo(ReasonKind.MISSION_REWARD);
		assertThat(entry.getReason().getDescription()).isEqualTo("Completed level 3");
	}

	@Test
	void creditForUnknownPlayerReturns404() throws Exception {
		mockMvc.perform(post("/players/{playerId}/wallet/credit", 999_999L)
						.contentType(MediaType.APPLICATION_JSON)
						.content(creditBody("10.00", UUID.randomUUID().toString(), "ADMIN", "test")))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("player_not_found"));
	}

	@Test
	void negativeOrZeroAmountReturns400AndLeavesBalanceUnchanged() throws Exception {
		long playerId = createPlayer();
		long walletId = wallets.findByPlayerId(playerId).orElseThrow().getId();

		for (String amount : new String[]{"-5.00", "0.00"}) {
			mockMvc.perform(post("/players/{playerId}/wallet/credit", playerId)
							.contentType(MediaType.APPLICATION_JSON)
							.content(creditBody(amount, UUID.randomUUID().toString(), "ADMIN", "test")))
					.andExpect(status().isBadRequest());
		}

		assertThat(balanceOf(playerId)).isZero();
		assertThat(ledgerEntries.findByWalletIdOrderByIdAsc(walletId)).isEmpty();
	}

	@Test
	void missingRequestIdReturns400AndLeavesBalanceUnchanged() throws Exception {
		long playerId = createPlayer();
		long walletId = wallets.findByPlayerId(playerId).orElseThrow().getId();

		mockMvc.perform(post("/players/{playerId}/wallet/credit", playerId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"amount\":\"10.00\",\"reason\":{\"reasonKind\":\"ADMIN\",\"description\":\"test\"}}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("missing_field"));

		assertThat(balanceOf(playerId)).isZero();
		assertThat(ledgerEntries.findByWalletIdOrderByIdAsc(walletId)).isEmpty();
	}

	@Test
	void invalidRequestIdReturns400AndLeavesBalanceUnchanged() throws Exception {
		long playerId = createPlayer();
		long walletId = wallets.findByPlayerId(playerId).orElseThrow().getId();

		// The first value is not a UUID; the second is UUID-shaped but 37 characters long,
		// wider than the request_id VARCHAR(36) column. Both are invalid input and must be
		// rejected with 400 — never left to crash the insert into a database error (500).
		for (String requestId : new String[]{"not-a-uuid", "12345678-1234-1234-1234-1234567890123"}) {
			mockMvc.perform(post("/players/{playerId}/wallet/credit", playerId)
						.contentType(MediaType.APPLICATION_JSON)
						.content(creditBody("10.00", requestId, "ADMIN", "test")))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.code").value("invalid_request_id"));
		}

		assertThat(balanceOf(playerId)).isZero();
		assertThat(ledgerEntries.findByWalletIdOrderByIdAsc(walletId)).isEmpty();
	}

	@Test
	void twoSequentialCreditsEachAppendOneEntryAndAddUp() throws Exception {
		long playerId = createPlayer();
		long walletId = wallets.findByPlayerId(playerId).orElseThrow().getId();

		mockMvc.perform(post("/players/{playerId}/wallet/credit", playerId)
						.contentType(MediaType.APPLICATION_JSON)
						.content(creditBody("10.00", UUID.randomUUID().toString(), "MISSION_REWARD", "Level 3")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.balance").value("10.00"));
		mockMvc.perform(post("/players/{playerId}/wallet/credit", playerId)
						.contentType(MediaType.APPLICATION_JSON)
						.content(creditBody("5.50", UUID.randomUUID().toString(), "PURCHASE", "Starter pack")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.balance").value("15.50"));

		assertThat(balanceOf(playerId)).isEqualTo(1550L);
		assertThat(ledgerEntries.findByWalletIdOrderByIdAsc(walletId)).hasSize(2);
	}

	private long balanceOf(long playerId) {
		return wallets.findByPlayerId(playerId).orElseThrow().getBalance();
	}

	private String creditBody(String amount, String requestId, String reasonKind, String description) {
		return "{"
				+ "\"amount\":\"" + amount + "\","
				+ "\"requestId\":\"" + requestId + "\","
				+ "\"reason\":{\"reasonKind\":\"" + reasonKind + "\",\"description\":\"" + description + "\"}"
				+ "}";
	}

	private long createPlayer() throws Exception {
		String body = mockMvc.perform(post("/players"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return JSON.readTree(body).get("playerId").asLong();
	}
}