package dev.wallet.api;

import com.fasterxml.jackson.databind.ObjectMapper;
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
class WalletIdempotencyControllerTest {

	private static final ObjectMapper JSON = new ObjectMapper();

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private WalletRepository wallets;

	@Autowired
	private LedgerEntryRepository ledgerEntries;

	@Test
	void repeatingACreditRequestIdAppliesOnceAndReturns200WithTheOriginalResult() throws Exception {
		long playerId = createPlayer();
		long walletId = wallets.findByPlayerId(playerId).orElseThrow().getId();
		String requestId = UUID.randomUUID().toString();

		mockMvc.perform(post("/players/{playerId}/wallet/credit", playerId)
						.contentType(MediaType.APPLICATION_JSON)
						.content(changeBody("10.00", requestId, "MISSION_REWARD", "Completed level 3")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.balance").value("10.00"));

		mockMvc.perform(post("/players/{playerId}/wallet/credit", playerId)
						.contentType(MediaType.APPLICATION_JSON)
						.content(changeBody("10.00", requestId, "MISSION_REWARD", "Completed level 3")))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.balance").value("10.00"));

		assertThat(balanceOf(playerId)).isEqualTo(1000L);
		assertThat(ledgerEntries.findByWalletIdOrderByIdAsc(walletId)).hasSize(1);
	}

	@Test
	void repeatingADebitRequestIdAppliesOnceAndReturns200WithTheOriginalResult() throws Exception {
		long playerId = createPlayer();
		long walletId = wallets.findByPlayerId(playerId).orElseThrow().getId();
		credit(playerId, "10.00", UUID.randomUUID().toString());
		String requestId = UUID.randomUUID().toString();

		mockMvc.perform(post("/players/{playerId}/wallet/debit", playerId)
						.contentType(MediaType.APPLICATION_JSON)
						.content(changeBody("4.00", requestId, "PURCHASE", "Starter pack")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.balance").value("6.00"));

		mockMvc.perform(post("/players/{playerId}/wallet/debit", playerId)
						.contentType(MediaType.APPLICATION_JSON)
						.content(changeBody("4.00", requestId, "PURCHASE", "Starter pack")))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.balance").value("6.00"));

		assertThat(balanceOf(playerId)).isEqualTo(600L);
		assertThat(ledgerEntries.findByWalletIdOrderByIdAsc(walletId)).hasSize(2);
	}

	@Test
	void twoConcurrentIdenticalCreditRequestsApplyOnce() throws Exception {
		long playerId = createPlayer();
		long walletId = wallets.findByPlayerId(playerId).orElseThrow().getId();
		String requestId = UUID.randomUUID().toString();

		List<Integer> statuses = concurrent(changeBody("10.00", requestId, "MISSION_REWARD", "Completed level 3"),
				"/players/{playerId}/wallet/credit", playerId);

		assertThat(statuses).containsExactly(200, 200);
		assertThat(balanceOf(playerId)).isEqualTo(1000L);
		assertThat(ledgerEntries.findByWalletIdOrderByIdAsc(walletId)).hasSize(1);
	}

	@Test
	void twoConcurrentIdenticalDebitRequestsApplyOnce() throws Exception {
		long playerId = createPlayer();
		long walletId = wallets.findByPlayerId(playerId).orElseThrow().getId();
		credit(playerId, "10.00", UUID.randomUUID().toString());
		String requestId = UUID.randomUUID().toString();

		List<Integer> statuses = concurrent(changeBody("4.00", requestId, "PURCHASE", "Starter pack"),
				"/players/{playerId}/wallet/debit", playerId);

		assertThat(statuses).containsExactly(200, 200);
		assertThat(balanceOf(playerId)).isEqualTo(600L);
		assertThat(ledgerEntries.findByWalletIdOrderByIdAsc(walletId)).hasSize(2);
	}

	private List<Integer> concurrent(String body, String url, long playerId) throws InterruptedException {
		int threads = 2;
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		CyclicBarrier barrier = new CyclicBarrier(threads);
		CountDownLatch done = new CountDownLatch(threads);
		List<Integer> statuses = Collections.synchronizedList(new ArrayList<>());

		for (int i = 0; i < threads; i++) {
			pool.execute(() -> {
				try {
					barrier.await(10, TimeUnit.SECONDS);
					int status = mockMvc.perform(post(url, playerId)
									.contentType(MediaType.APPLICATION_JSON)
									.content(body))
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
		return statuses;
	}

	private void credit(long playerId, String amount, String requestId) throws Exception {
		mockMvc.perform(post("/players/{playerId}/wallet/credit", playerId)
						.contentType(MediaType.APPLICATION_JSON)
						.content(changeBody(amount, requestId, "MISSION_REWARD", "Completed level 3")))
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