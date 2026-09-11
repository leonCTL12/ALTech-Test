package dev.wallet.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.wallet.domain.Direction;
import dev.wallet.domain.LedgerEntry;
import dev.wallet.repository.LedgerEntryRepository;
import dev.wallet.repository.WalletRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class WalletConcurrentDebitControllerTest {

	private static final ObjectMapper JSON = new ObjectMapper();

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private WalletRepository wallets;

	@Autowired
	private LedgerEntryRepository ledgerEntries;

	@Test
	void twoConcurrentDebitsThatWouldBothOverdrawLeaveExactBalanceAndRejectTheLoserWith409() throws Exception {
		long playerId = createPlayer();
		long walletId = wallets.findByPlayerId(playerId).orElseThrow().getId();
		credit(playerId, "10.00");

		List<DebitResult> results = concurrentDebits(playerId, "6.00", "6.00");

		assertThat(results).extracting(DebitResult::status).containsExactlyInAnyOrder(200, 409);
		assertThat(results).filteredOn(r -> r.status() == 409)
				.allMatch(r -> r.code().equals("insufficient_balance"));
		assertThat(results).filteredOn(r -> r.status() == 200)
				.allMatch(r -> r.balance().equals("4.00"));
		assertThat(balanceOf(playerId)).isEqualTo(400L);
		assertThat(debitEntriesOf(walletId)).hasSize(1);
	}

	@Test
	void twoConcurrentAffordableDebitsBothSucceedWithNoLostUpdate() throws Exception {
		long playerId = createPlayer();
		long walletId = wallets.findByPlayerId(playerId).orElseThrow().getId();
		credit(playerId, "10.00");

		List<DebitResult> results = concurrentDebits(playerId, "3.00", "4.00");

		assertThat(results).extracting(DebitResult::status).containsExactly(200, 200);
		assertThat(balanceOf(playerId)).isEqualTo(300L);
		assertThat(debitEntriesOf(walletId)).extracting(LedgerEntry::getAmount)
				.containsExactlyInAnyOrder(300L, 400L);
	}

	@Test
	void manyConcurrentDebitsNeverOverdrawAndEveryAppliedDebitIsRecorded() throws Exception {
		long playerId = createPlayer();
		long walletId = wallets.findByPlayerId(playerId).orElseThrow().getId();
		credit(playerId, "5.00");

		List<DebitResult> results = concurrentDebits(playerId,
				"1.00", "1.00", "1.00", "1.00", "1.00", "1.00", "1.00", "1.00");

		assertThat(results).hasSize(8);
		long succeeded = results.stream().filter(r -> r.status() == 200).count();
		assertThat(succeeded).isEqualTo(5);
		assertThat(results).filteredOn(r -> r.status() == 409)
				.allMatch(r -> r.code().equals("insufficient_balance"));
		assertThat(balanceOf(playerId)).isZero();
		assertThat(debitEntriesOf(walletId)).hasSize(5);
	}

	private List<DebitResult> concurrentDebits(long playerId, String... amounts) throws InterruptedException {
		int threads = amounts.length;
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		CyclicBarrier barrier = new CyclicBarrier(threads);
		CountDownLatch done = new CountDownLatch(threads);
		List<DebitResult> results = Collections.synchronizedList(new ArrayList<>());

		for (String amount : amounts) {
			pool.execute(() -> {
				try {
					barrier.await(10, TimeUnit.SECONDS);
					MvcResult mvcResult = mockMvc.perform(post("/players/{playerId}/wallet/debit", playerId)
									.contentType(MediaType.APPLICATION_JSON)
									.content(changeBody(amount, UUID.randomUUID().toString(),
											"PURCHASE", "Concurrent debit")))
							.andReturn();
					results.add(parse(mvcResult));
				} catch (Exception e) {
					throw new RuntimeException(e);
				} finally {
					done.countDown();
				}
			});
		}

		boolean allFinished = done.await(30, TimeUnit.SECONDS);
		pool.shutdown();
		if (!allFinished) {
			throw new AssertionError("Concurrent debits did not finish within 30 seconds");
		}
		return results;
	}

	private DebitResult parse(MvcResult mvcResult) throws Exception {
		JsonNode body = JSON.readTree(mvcResult.getResponse().getContentAsString());
		int httpStatus = mvcResult.getResponse().getStatus();
		String code = httpStatus == 200 ? null
				: body.has("code") ? body.get("code").asText() : null;
		String balance = httpStatus == 200 ? body.get("balance").asText() : null;
		return new DebitResult(httpStatus, code, balance);
	}

	private List<LedgerEntry> debitEntriesOf(long walletId) {
		return ledgerEntries.findByWalletIdOrderByIdAsc(walletId).stream()
				.filter(entry -> entry.getDirection() == Direction.DEBIT)
				.toList();
	}

	private void credit(long playerId, String amount) throws Exception {
		mockMvc.perform(post("/players/{playerId}/wallet/credit", playerId)
						.contentType(MediaType.APPLICATION_JSON)
						.content(changeBody(amount, UUID.randomUUID().toString(),
								"MISSION_REWARD", "Completed level 3")))
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

	private record DebitResult(int status, String code, String balance) {
	}
}