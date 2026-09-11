package dev.wallet.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.wallet.domain.Direction;
import dev.wallet.domain.LedgerEntry;
import dev.wallet.domain.ReasonKind;
import dev.wallet.repository.LedgerEntryRepository;
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
class WalletDebitControllerTest {

	private static final ObjectMapper JSON = new ObjectMapper();

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private WalletRepository wallets;

	@Autowired
	private LedgerEntryRepository ledgerEntries;

	@Test
	void debitDecreasesBalanceAndAppendsOneDebitLedgerEntryWithTheReason() throws Exception {
		long playerId = createPlayer();
		long walletId = wallets.findByPlayerId(playerId).orElseThrow().getId();
		credit(playerId, "10.00", "MISSION_REWARD", "Completed level 3");
		String requestId = UUID.randomUUID().toString();

		mockMvc.perform(post("/players/{playerId}/wallet/debit", playerId)
						.contentType(MediaType.APPLICATION_JSON)
						.content(changeBody("4.00", requestId, "PURCHASE", "Starter pack")))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.balance").value("6.00"));

		assertThat(balanceOf(playerId)).isEqualTo(600L);

		List<LedgerEntry> entries = ledgerEntries.findByWalletIdOrderByIdAsc(walletId);
		assertThat(entries).hasSize(2);
		LedgerEntry entry = entries.get(1);
		assertThat(entry.getAmount()).isEqualTo(400L);
		assertThat(entry.getRequestId()).isEqualTo(requestId);
		assertThat(entry.getDirection()).isEqualTo(Direction.DEBIT);
		assertThat(entry.getWallet().getId()).isEqualTo(walletId);
		assertThat(entry.getReason().getKind()).isEqualTo(ReasonKind.PURCHASE);
		assertThat(entry.getReason().getDescription()).isEqualTo("Starter pack");
	}

	@Test
	void debitLargerThanBalanceReturns409AndLeavesBalanceUnchanged() throws Exception {
		long playerId = createPlayer();
		long walletId = wallets.findByPlayerId(playerId).orElseThrow().getId();
		credit(playerId, "10.00", "MISSION_REWARD", "Completed level 3");

		mockMvc.perform(post("/players/{playerId}/wallet/debit", playerId)
						.contentType(MediaType.APPLICATION_JSON)
						.content(changeBody("10.01", UUID.randomUUID().toString(), "PURCHASE", "Too expensive")))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("insufficient_balance"));

		assertThat(balanceOf(playerId)).isEqualTo(1000L);
		assertThat(ledgerEntries.findByWalletIdOrderByIdAsc(walletId)).hasSize(1);
	}

	@Test
	void debitExactlyEqualToBalanceSucceedsAndLeavesZero() throws Exception {
		long playerId = createPlayer();
		credit(playerId, "10.00", "MISSION_REWARD", "Completed level 3");

		mockMvc.perform(post("/players/{playerId}/wallet/debit", playerId)
						.contentType(MediaType.APPLICATION_JSON)
						.content(changeBody("10.00", UUID.randomUUID().toString(), "PURCHASE", "All in")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.balance").value("0.00"));

		assertThat(balanceOf(playerId)).isZero();
	}

	@Test
	void debitForUnknownPlayerReturns404() throws Exception {
		mockMvc.perform(post("/players/{playerId}/wallet/debit", 999_999L)
						.contentType(MediaType.APPLICATION_JSON)
						.content(changeBody("10.00", UUID.randomUUID().toString(), "ADMIN", "test")))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("player_not_found"));
	}

	@Test
	void negativeOrZeroAmountReturns400AndLeavesBalanceUnchanged() throws Exception {
		long playerId = createPlayer();
		long walletId = wallets.findByPlayerId(playerId).orElseThrow().getId();
		credit(playerId, "10.00", "MISSION_REWARD", "Completed level 3");

		for (String amount : new String[]{"-5.00", "0.00"}) {
			mockMvc.perform(post("/players/{playerId}/wallet/debit", playerId)
							.contentType(MediaType.APPLICATION_JSON)
							.content(changeBody(amount, UUID.randomUUID().toString(), "ADMIN", "test")))
					.andExpect(status().isBadRequest());
		}

		assertThat(balanceOf(playerId)).isEqualTo(1000L);
		assertThat(ledgerEntries.findByWalletIdOrderByIdAsc(walletId)).hasSize(1);
	}

	private void credit(long playerId, String amount, String reasonKind, String description) throws Exception {
		mockMvc.perform(post("/players/{playerId}/wallet/credit", playerId)
						.contentType(MediaType.APPLICATION_JSON)
						.content(changeBody(amount, UUID.randomUUID().toString(), reasonKind, description)))
				.andExpect(status().isOk());
	}

	private long balanceOf(long playerId) {
		return wallets.findByPlayerId(playerId).orElseThrow().getBalance();
	}

	private String changeBody(String amount, String requestId, String reasonKind, String description) {
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