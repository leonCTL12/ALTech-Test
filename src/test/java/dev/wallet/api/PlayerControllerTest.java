package dev.wallet.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.wallet.repository.PlayerRepository;
import dev.wallet.repository.WalletRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PlayerControllerTest {

	private static final ObjectMapper JSON = new ObjectMapper();

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private PlayerRepository players;

	@Autowired
	private WalletRepository wallets;

	@Test
	void createPlayerReturns201WithServerGeneratedPlayerId() throws Exception {
		mockMvc.perform(post("/players"))
				.andExpect(status().isCreated())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.playerId").isNumber());
	}

	@Test
	void createPlayerPersistsPlayerAndWalletAtZeroBalance() throws Exception {
		long playerId = createPlayer();

		assertThat(players.findById(playerId)).isPresent();
		assertThat(wallets.findByPlayerId(playerId))
				.hasValueSatisfying(wallet -> {
					assertThat(wallet.getBalance()).isZero();
					assertThat(wallet.getPlayer().getId()).isEqualTo(playerId);
				});
	}

	@Test
	void twoCreateCallsProduceIndependentPlayersEachWithItsOwnWallet() throws Exception {
		long first = createPlayer();
		long second = createPlayer();

		assertThat(first).isNotEqualTo(second);
		assertThat(wallets.findByPlayerId(first).orElseThrow().getId())
				.isNotEqualTo(wallets.findByPlayerId(second).orElseThrow().getId());
		assertThat(wallets.findByPlayerId(first).orElseThrow().getBalance()).isZero();
		assertThat(wallets.findByPlayerId(second).orElseThrow().getBalance()).isZero();
	}

	@Test
	void malformedJsonBodyReturns400WithStructuredError() throws Exception {
		mockMvc.perform(post("/players")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{oops"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("malformed_body"))
				.andExpect(jsonPath("$.message").isNotEmpty())
				.andExpect(jsonPath("$.field").value(nullValue()));
	}

	@Test
	void nonEmptyBodyReturns400WithStructuredError() throws Exception {
		mockMvc.perform(post("/players")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"foo\":\"bar\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("non_empty_body"))
				.andExpect(jsonPath("$.message").isNotEmpty())
				.andExpect(jsonPath("$.field").value(nullValue()));
	}

	@Test
	void emptyObjectBodyIsAcceptedAndCreatesAPlayer() throws Exception {
		mockMvc.perform(post("/players")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.playerId").isNumber());
	}

	private long createPlayer() throws Exception {
		String body = mockMvc.perform(post("/players"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return JSON.readTree(body).get("playerId").asLong();
	}
}