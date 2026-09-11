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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class WalletRefundControllerTest {

	private static final ObjectMapper JSON = new ObjectMapper();

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private WalletRepository wallets;

	@Autowired
	private LedgerEntryRepository ledgerEntries;

	@Test
	void refundRestoresTheAmountAndAppendsOneCreditLedgerEntryForTheDebit() throws Exception {
		long playerId = createPlayer();
		long walletId = wallets.findByPlayerId(playerId).orElseThrow().getId();
		credit(playerId, "10.00");
		long debitId = debit(playerId, "4.00");

		mockMvc.perform(post("/players/{playerId}/wallet/refund", playerId)
						.contentType(MediaType.APPLICATION_JSON)
						.content(refundBody("4.00", UUID.randomUUID().toString(), debitId)))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.balance").value("10.00"));

		assertThat(balanceOf(playerId)).isEqualTo(1000L);

		List<LedgerEntry> entries = ledgerEntries.findByWalletIdOrderByIdAsc(walletId);
		assertThat(entries).hasSize(3);
		LedgerEntry refund = entries.get(2);
		assertThat(refund.getDirection()).isEqualTo(Direction.CREDIT);
		assertThat(refund.getAmount()).isEqualTo(400L);
		assertThat(refund.getOriginalDebitId()).isEqualTo(debitId);
		assertThat(refund.getReason().getKind()).isEqualTo(ReasonKind.REFUND);
	}

	@Test
	void refundingTheSameDebitTwiceReturns409AndLeavesBalanceUnchanged() throws Exception {
		long playerId = createPlayer();
		long walletId = wallets.findByPlayerId(playerId).orElseThrow().getId();
		credit(playerId, "10.00");
		long debitId = debit(playerId, "4.00");

		refund(playerId, "4.00", debitId, UUID.randomUUID().toString());
		assertThat(balanceOf(playerId)).isEqualTo(1000L);

		mockMvc.perform(post("/players/{playerId}/wallet/refund", playerId)
						.contentType(MediaType.APPLICATION_JSON)
						.content(refundBody("4.00", UUID.randomUUID().toString(), debitId)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("already_refunded"));

		assertThat(balanceOf(playerId)).isEqualTo(1000L);
		assertThat(ledgerEntries.findByWalletIdOrderByIdAsc(walletId)).hasSize(3);
	}

	@Test
	void refundLargerThanCurrentBalanceSucceedsWithoutOverdraftGuard() throws Exception {
		long playerId = createPlayer();
		long walletId = wallets.findByPlayerId(playerId).orElseThrow().getId();
		credit(playerId, "10.00");
		long debitId = debit(playerId, "5.00");

		mockMvc.perform(post("/players/{playerId}/wallet/refund", playerId)
						.contentType(MediaType.APPLICATION_JSON)
						.content(refundBody("10.00", UUID.randomUUID().toString(), debitId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.balance").value("15.00"));

		assertThat(balanceOf(playerId)).isEqualTo(1500L);
		assertThat(ledgerEntries.findByWalletIdOrderByIdAsc(walletId)).hasSize(3);
	}

	@Test
	void retryingTheSameRefundRequestIdReturns200WithTheOriginalResultAndAppliesOnlyOnce() throws Exception {
		long playerId = createPlayer();
		long walletId = wallets.findByPlayerId(playerId).orElseThrow().getId();
		credit(playerId, "10.00");
		long debitId = debit(playerId, "4.00");
		String requestId = UUID.randomUUID().toString();

		refund(playerId, "4.00", debitId, requestId);
		assertThat(balanceOf(playerId)).isEqualTo(1000L);

		mockMvc.perform(post("/players/{playerId}/wallet/refund", playerId)
						.contentType(MediaType.APPLICATION_JSON)
						.content(refundBody("4.00", requestId, debitId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.balance").value("10.00"));

		assertThat(balanceOf(playerId)).isEqualTo(1000L);
		assertThat(ledgerEntries.findByWalletIdOrderByIdAsc(walletId)).hasSize(3);
	}

	@Test
	void refundForUnknownPlayerReturns404() throws Exception {
		mockMvc.perform(post("/players/{playerId}/wallet/refund", 999_999L)
						.contentType(MediaType.APPLICATION_JSON)
						.content(refundBody("4.00", UUID.randomUUID().toString(), 1L)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("player_not_found"));
	}

	@Test
	void refundWithUnknownOriginalDebitReturns404() throws Exception {
		long playerId = createPlayer();
		credit(playerId, "10.00");

		mockMvc.perform(post("/players/{playerId}/wallet/refund", playerId)
						.contentType(MediaType.APPLICATION_JSON)
						.content(refundBody("4.00", UUID.randomUUID().toString(), 999_999L)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("debit_not_found"));
	}

	@Test
	void refundWithoutOriginalDebitIdReturns400() throws Exception {
		long playerId = createPlayer();
		credit(playerId, "10.00");

		mockMvc.perform(post("/players/{playerId}/wallet/refund", playerId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{"
								+ "\"amount\":\"4.00\","
								+ "\"requestId\":\"" + UUID.randomUUID() + "\","
								+ "\"reason\":{\"reasonKind\":\"REFUND\",\"description\":\"test\"}"
								+ "}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("missing_field"));
	}

	@Test
	void twoConcurrentRefundsOfTheSameDebitApplyOnlyOnce() throws Exception {
		long playerId = createPlayer();
		long walletId = wallets.findByPlayerId(playerId).orElseThrow().getId();
		credit(playerId, "10.00");
		long debitId = debit(playerId, "4.00");

		int threads = 2;
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		CyclicBarrier barrier = new CyclicBarrier(threads);
		CountDownLatch done = new CountDownLatch(threads);
		List<Integer> statuses = Collections.synchronizedList(new ArrayList<>());

		for (int i = 0; i < threads; i++) {
			pool.execute(() -> {
				try {
					barrier.await(10, TimeUnit.SECONDS);
					int status = mockMvc.perform(post("/players/{playerId}/wallet/refund", playerId)
									.contentType(MediaType.APPLICATION_JSON)
									.content(refundBody("4.00", UUID.randomUUID().toString(), debitId)))
							.andReturn().getResponse().getStatus();
					statuses.add(status);
				} catch (Exception e) {
					throw new RuntimeException(e);
				} finally {
					done.countDown();
				}
			});
		}

		done.await(30, TimeUnit.SECONDS);
		pool.shutdown();

		assertThat(statuses).containsExactlyInAnyOrder(200, 409);
		assertThat(balanceOf(playerId)).isEqualTo(1000L);
		List<LedgerEntry> entries = ledgerEntries.findByWalletIdOrderByIdAsc(walletId);
		assertThat(entries).hasSize(3);
		assertThat(entries.stream().filter(e -> e.getOriginalDebitId() != null)).hasSize(1);
	}

	private long debit(long playerId, String amount) throws Exception {
		mockMvc.perform(post("/players/{playerId}/wallet/debit", playerId)
						.contentType(MediaType.APPLICATION_JSON)
						.content(changeBody(amount, UUID.randomUUID().toString(), "PURCHASE", "Starter pack")))
				.andExpect(status().isOk());
		long walletId = wallets.findByPlayerId(playerId).orElseThrow().getId();
		return ledgerEntries.findByWalletIdOrderByIdAsc(walletId).stream()
				.filter(e -> e.getDirection() == Direction.DEBIT)
				.map(LedgerEntry::getId)
				.findFirst().orElseThrow();
	}

	private void refund(long playerId, String amount, long debitId, String requestId) throws Exception {
		mockMvc.perform(post("/players/{playerId}/wallet/refund", playerId)
						.contentType(MediaType.APPLICATION_JSON)
						.content(refundBody(amount, requestId, debitId)))
				.andExpect(status().isOk());
	}

	private void credit(long playerId, String amount) throws Exception {
		mockMvc.perform(post("/players/{playerId}/wallet/credit", playerId)
						.contentType(MediaType.APPLICATION_JSON)
						.content(changeBody(amount, UUID.randomUUID().toString(), "MISSION_REWARD", "Completed level 3")))
				.andExpect(status().isOk());
	}

	private long balanceOf(long playerId) {
		return wallets.findByPlayerId(playerId).orElseThrow().getBalance();
	}

	private String refundBody(String amount, String requestId, long debitId) {
		return "{"
				+ "\"amount\":\"" + amount + "\","
				+ "\"requestId\":\"" + requestId + "\","
				+ "\"reason\":{\"reasonKind\":\"REFUND\",\"description\":\"Refund of purchase\"},"
				+ "\"originalDebitId\":" + debitId
				+ "}";
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
