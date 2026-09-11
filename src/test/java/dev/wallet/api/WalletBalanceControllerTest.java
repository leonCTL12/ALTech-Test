package dev.wallet.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class WalletBalanceControllerTest {

	private static final ObjectMapper JSON = new ObjectMapper();

	@Autowired
	private MockMvc mockMvc;

	@Test
	void freshPlayerWalletReturnsZeroBalance() throws Exception {
		long playerId = createPlayer();

		mockMvc.perform(get("/players/{playerId}/wallet", playerId))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.balance").value("0.00"));
	}

	@Test
	void getReturnsBalanceInDecimalFormAfterACredit() throws Exception {
		long playerId = createPlayer();

		mockMvc.perform(post("/players/{playerId}/wallet/credit", playerId)
						.contentType(MediaType.APPLICATION_JSON)
						.content(creditBody("12.34", UUID.randomUUID().toString(), "MISSION_REWARD", "Level up")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.balance").value("12.34"));

		mockMvc.perform(get("/players/{playerId}/wallet", playerId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.balance").value("12.34"));
	}

	@Test
	void unknownPlayerReturns404() throws Exception {
		mockMvc.perform(get("/players/{playerId}/wallet", 999_999L))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("player_not_found"));
	}

	@Test
	void nonNumericPlayerIdReturns400() throws Exception {
		mockMvc.perform(get("/players/{playerId}/wallet", "abc"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("invalid_path"));
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