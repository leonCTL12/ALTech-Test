package dev.wallet.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.wallet.api.error.ApiException;
import dev.wallet.service.PlayerService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/players")
public class PlayerController {

	private final PlayerService playerService;
	private final ObjectMapper json;

	public PlayerController(PlayerService playerService, ObjectMapper json) {
		this.playerService = playerService;
		this.json = json;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public CreatePlayerResponse createPlayer(@RequestBody(required = false) String body) {
		requireEmptyBody(body);
		return new CreatePlayerResponse(playerService.createPlayer());
	}

	private void requireEmptyBody(String body) {
		if (body == null || body.isBlank()) {
			return;
		}
		try {
			json.readTree(body);
		} catch (JsonProcessingException e) {
			throw ApiException.malformedJson();
		}
		throw new ApiException(HttpStatus.BAD_REQUEST, "non_empty_body",
				"This endpoint expects an empty request body.");
	}

	public record CreatePlayerResponse(long playerId) {
	}
}