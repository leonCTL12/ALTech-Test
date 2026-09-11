package dev.wallet.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.wallet.api.error.ApiException;
import dev.wallet.api.error.ErrorResponse;
import dev.wallet.domain.LedgerEntry;
import dev.wallet.domain.MinorUnits;
import dev.wallet.domain.Reason;
import dev.wallet.domain.ReasonKind;
import dev.wallet.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/players/{playerId}/wallet")
public class WalletController {

	private static final int DEFAULT_LIMIT = 50;

	private static final int MAX_LIMIT = 100;

	private final WalletService walletService;

	public WalletController(WalletService walletService) {
		this.walletService = walletService;
	}

	@GetMapping
	@Operation(summary = "Get a player's wallet balance",
			description = "Returns the current balance of the player's wallet as a decimal string.")
	@ApiResponse(responseCode = "200", description = "The current balance",
			content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = BalanceResponse.class)))
	@ApiResponse(responseCode = "400", description = "playerId is not a positive whole number (code: invalid_path)",
			content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class)))
	@ApiResponse(responseCode = "404", description = "No player with the given id exists (code: player_not_found)",
			content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class)))
	public BalanceResponse getBalance(@PathVariable long playerId) {
		requirePlayerId(playerId);
		return new BalanceResponse(walletService.getBalance(playerId));
	}

	@GetMapping("/transactions")
	@Operation(summary = "Get a player's ledger entry history",
			description = "Returns the ledger entries of the player's wallet, newest first, using cursor pagination. "
					+ "Omit after to start from the newest entry; limit caps the page size.")
	@ApiResponse(responseCode = "200", description = "A page of ledger entries and the cursor for the next page",
			content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = LedgerHistoryResponse.class)))
	@ApiResponse(responseCode = "400", description = "playerId, after or limit is invalid (codes: invalid_path, invalid_cursor, invalid_limit)",
			content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class)))
	@ApiResponse(responseCode = "404", description = "No player with the given id exists (code: player_not_found)",
			content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class)))
	public LedgerHistoryResponse transactions(@PathVariable long playerId,
			@Parameter(description = "Ledger entry id to continue from; entries newer than this are omitted")
			@RequestParam(required = false) String after,
			@Parameter(description = "Maximum number of entries to return, between 1 and " + MAX_LIMIT)
			@RequestParam(required = false) String limit) {
		requirePlayerId(playerId);
		WalletService.LedgerHistory history =
				walletService.history(playerId, toCursor(after), toLimit(limit));
		return LedgerHistoryResponse.from(history.entries(), history.nextAfter());
	}

	@PostMapping("/credit")
	@Operation(summary = "Credit a player's wallet",
			description = "Increases the wallet balance by the given amount and appends a credit ledger entry. "
					+ "Retrying with the same requestId applies the credit only once.")
	@ApiResponse(responseCode = "200", description = "The balance after the credit",
			content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = BalanceResponse.class)))
	@ApiResponse(responseCode = "400", description = "A field is missing or invalid (codes: missing_field, invalid_amount, malformed_body)",
			content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class)))
	@ApiResponse(responseCode = "404", description = "No player with the given id exists (code: player_not_found)",
			content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class)))
	public BalanceResponse credit(@PathVariable long playerId, @RequestBody AmountRequest request) {
		ChangeRequest change = parse(playerId, request.amount(), request.requestId(), request.reason());
		return new BalanceResponse(walletService.credit(playerId, change.amount(), change.reason(), change.requestId()));
	}

	@PostMapping("/debit")
	@Operation(summary = "Debit a player's wallet",
			description = "Decreases the wallet balance by the given amount and appends a debit ledger entry. "
					+ "Rejected with 409 if the balance is not enough. "
					+ "Retrying with the same requestId applies the debit only once.")
	@ApiResponse(responseCode = "200", description = "The balance after the debit",
			content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = BalanceResponse.class)))
	@ApiResponse(responseCode = "400", description = "A field is missing or invalid (codes: missing_field, invalid_amount, malformed_body)",
			content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class)))
	@ApiResponse(responseCode = "404", description = "No player with the given id exists (code: player_not_found)",
			content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class)))
	@ApiResponse(responseCode = "409", description = "The balance is not enough to cover the debit (code: insufficient_balance)",
			content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class)))
	public BalanceResponse debit(@PathVariable long playerId, @RequestBody AmountRequest request) {
		ChangeRequest change = parse(playerId, request.amount(), request.requestId(), request.reason());
		return new BalanceResponse(walletService.debit(playerId, change.amount(), change.reason(), change.requestId()));
	}

	@PostMapping("/refund")
	@Operation(summary = "Refund a prior debit",
			description = "Credits the wallet to reverse a prior debit, referencing the original debit's ledger entry. "
					+ "A refund is never subject to the overdraft guard, and a debit can be refunded only once. "
					+ "Retrying with the same requestId applies the refund only once.")
	@ApiResponse(responseCode = "200", description = "The balance after the refund",
			content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = BalanceResponse.class)))
	@ApiResponse(responseCode = "400", description = "A field is missing or invalid (codes: missing_field, invalid_amount, invalid_original_debit_id, malformed_body)",
			content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class)))
	@ApiResponse(responseCode = "404", description = "The player or the original debit does not exist (codes: player_not_found, debit_not_found)",
			content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class)))
	@ApiResponse(responseCode = "409", description = "The original debit has already been refunded (code: already_refunded)",
			content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class)))
	public BalanceResponse refund(@PathVariable long playerId, @RequestBody RefundRequest request) {
		ChangeRequest change = parse(playerId, request.amount(), request.requestId(), request.reason());
		requireOriginalDebitId(request.originalDebitId());
		return new BalanceResponse(walletService.refund(playerId, change.amount(), change.reason(),
				change.requestId(), request.originalDebitId()));
	}

	private void requireOriginalDebitId(Long originalDebitId) {
		if (originalDebitId == null) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "missing_field",
					"The field 'originalDebitId' is required.", "originalDebitId");
		}
		if (originalDebitId <= 0) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_original_debit_id",
					"originalDebitId must be a positive whole number.", "originalDebitId");
		}
	}

	private ChangeRequest parse(long playerId, String amount, String requestId, ReasonInput reason) {
		requirePlayerId(playerId);
		require(amount, "amount");
		require(requestId, "requestId");
		requireReason(reason);
		MinorUnits minor = toMinorUnits(amount);
		Reason r = new Reason(reason.kind(), reason.description(), reason.referenceId());
		return new ChangeRequest(minor, r, requestId.trim());
	}

	private record ChangeRequest(MinorUnits amount, Reason reason, String requestId) {
	}

	private MinorUnits toMinorUnits(String decimal) {
		try {
			return MinorUnits.fromDecimal(decimal);
		} catch (IllegalArgumentException e) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_amount", e.getMessage(), "amount");
		}
	}

	private Long toCursor(String after) {
		if (after == null || after.isBlank()) {
			return null;
		}
		return parsePositive(after, Long.MAX_VALUE, "invalid_cursor",
				"after must be a positive whole number.", "after");
	}

	private int toLimit(String limit) {
		if (limit == null || limit.isBlank()) {
			return DEFAULT_LIMIT;
		}
		return (int) parsePositive(limit, MAX_LIMIT, "invalid_limit",
				"limit must be a whole number between 1 and " + MAX_LIMIT + ".", "limit");
	}

	private long parsePositive(String raw, long max, String code, String message, String field) {
		long value;
		try {
			value = Long.parseLong(raw);
		} catch (NumberFormatException e) {
			throw new ApiException(HttpStatus.BAD_REQUEST, code, message, field);
		}
		if (value <= 0 || value > max) {
			throw new ApiException(HttpStatus.BAD_REQUEST, code, message, field);
		}
		return value;
	}

	private void requirePlayerId(long playerId) {
		if (playerId <= 0) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_path",
					"playerId must be a positive whole number.", "playerId");
		}
	}

	private void require(String value, String field) {
		if (value == null || value.isBlank()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "missing_field",
					"The field '" + field + "' is required.", field);
		}
	}

	private void requireReason(ReasonInput reason) {
		if (reason == null) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "missing_field",
					"The field 'reason' is required.", "reason");
		}
		if (reason.kind() == null) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "missing_field",
					"The field 'reason.reasonKind' is required.", "reason.reasonKind");
		}
		require(reason.description(), "reason.description");
	}

	public record AmountRequest(String amount, String requestId, ReasonInput reason) {
	}

	public record RefundRequest(String amount, String requestId, ReasonInput reason, Long originalDebitId) {
	}

	public record ReasonInput(@JsonProperty("reasonKind") ReasonKind kind, String description, Long referenceId) {
	}

	public record BalanceResponse(String balance) {
	}

	public record LedgerHistoryResponse(List<LedgerEntryResponse> entries, Long nextAfter) {

		static LedgerHistoryResponse from(List<LedgerEntry> entries, Long nextAfter) {
			return new LedgerHistoryResponse(entries.stream().map(LedgerEntryResponse::from).toList(), nextAfter);
		}
	}

	public record LedgerEntryResponse(long id, String amount, String direction, ReasonResponse reason, String createdAt) {

		static LedgerEntryResponse from(LedgerEntry entry) {
			return new LedgerEntryResponse(entry.getId(), MinorUnits.format(entry.getAmount()),
					entry.getDirection().name(), ReasonResponse.from(entry.getReason()), entry.getCreatedAt().toString());
		}
	}

	public record ReasonResponse(String reasonKind, String description, Long referenceId) {

		static ReasonResponse from(Reason reason) {
			return new ReasonResponse(reason.getKind().name(), reason.getDescription(), reason.getReferenceId());
		}
	}
}