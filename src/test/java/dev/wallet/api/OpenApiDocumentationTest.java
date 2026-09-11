package dev.wallet.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OpenApiDocumentationTest {

	private static final ObjectMapper JSON = new ObjectMapper();

	@Autowired
	private MockMvc mockMvc;

	@Test
	void servesTheOpenApiDescriptionAsJson() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
	}

	@Test
	void servesTheSwaggerUi() throws Exception {
		mockMvc.perform(get("/swagger-ui/index.html"))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
	}

	@Test
	void documentsEveryEndpointWithItsErrorResponses() throws Exception {
		String body = mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		JsonNode doc = JSON.readTree(body);
		JsonNode paths = doc.get("paths");

		assertThat(paths.has("/players")).as("create player path").isTrue();
		assertThat(paths.has("/players/{playerId}/wallet")).as("get balance path").isTrue();
		assertThat(paths.has("/players/{playerId}/wallet/transactions")).as("history path").isTrue();
		assertThat(paths.has("/players/{playerId}/wallet/credit")).as("credit path").isTrue();
		assertThat(paths.has("/players/{playerId}/wallet/debit")).as("debit path").isTrue();
		assertThat(paths.has("/players/{playerId}/wallet/refund")).as("refund path").isTrue();

		JsonNode createPlayerResponses = paths.path("/players").path("post").path("responses");
		assertThat(createPlayerResponses.path("201").path("content")).as("create player 201 body").isNotEmpty();
		assertThat(createPlayerResponses.path("400").path("content")).as("create player 400 body").isNotEmpty();

		JsonNode balanceResponses = paths.path("/players/{playerId}/wallet").path("get").path("responses");
		assertThat(balanceResponses.path("200").path("content")).as("balance 200 body").isNotEmpty();
		assertThat(balanceResponses.path("400").path("content")).as("balance 400 body").isNotEmpty();
		assertThat(balanceResponses.path("404").path("content")).as("balance 404 body").isNotEmpty();

		JsonNode creditResponses = paths.path("/players/{playerId}/wallet/credit").path("post").path("responses");
		assertThat(creditResponses.path("200").path("content")).as("credit 200 body").isNotEmpty();
		assertThat(creditResponses.path("400").path("content")).as("credit 400 body").isNotEmpty();
		assertThat(creditResponses.path("404").path("content")).as("credit 404 body").isNotEmpty();

		JsonNode debitResponses = paths.path("/players/{playerId}/wallet/debit").path("post").path("responses");
		assertThat(debitResponses.path("200").path("content")).as("debit 200 body").isNotEmpty();
		assertThat(debitResponses.path("400").path("content")).as("debit 400 body").isNotEmpty();
		assertThat(debitResponses.path("404").path("content")).as("debit 404 body").isNotEmpty();
		assertThat(debitResponses.path("409").path("content")).as("debit 409 body").isNotEmpty();

		JsonNode refundResponses = paths.path("/players/{playerId}/wallet/refund").path("post").path("responses");
		assertThat(refundResponses.path("200").path("content")).as("refund 200 body").isNotEmpty();
		assertThat(refundResponses.path("400").path("content")).as("refund 400 body").isNotEmpty();
		assertThat(refundResponses.path("404").path("content")).as("refund 404 body").isNotEmpty();
		assertThat(refundResponses.path("409").path("content")).as("refund 409 body").isNotEmpty();

		JsonNode historyResponses = paths.path("/players/{playerId}/wallet/transactions").path("get").path("responses");
		assertThat(historyResponses.path("200").path("content")).as("history 200 body").isNotEmpty();
		assertThat(historyResponses.path("400").path("content")).as("history 400 body").isNotEmpty();
		assertThat(historyResponses.path("404").path("content")).as("history 404 body").isNotEmpty();

		JsonNode transactionsParameters = paths.path("/players/{playerId}/wallet/transactions").path("get").path("parameters");
		assertThat(transactionsParameters).as("history parameters").isNotEmpty();
	}
}
