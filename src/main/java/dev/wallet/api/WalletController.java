package dev.wallet.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.wallet.api.error.ApiException;
import dev.wallet.domain.MinorUnits;
import dev.wallet.domain.Reason;
import dev.wallet.domain.ReasonKind;
import dev.wallet.service.WalletService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/players/{playerId}/wallet")
public class WalletController {

	private final WalletService walletService;

	public WalletController(WalletService walletService) {
		this.walletService = walletService;
	}

	@GetMapping
	public BalanceResponse getBalance(@PathVariable long playerId) {
		requirePlayerId(playerId);
		return new BalanceResponse(walletService.getBalance(playerId));
	}

	@PostMapping("/credit")
	public BalanceResponse credit(@PathVariable long playerId, @RequestBody AmountRequest request) {
		ChangeRequest change = parse(playerId, request.amount(), request.requestId(), request.reason());
		return new BalanceResponse(walletService.credit(playerId, change.amount(), change.reason(), change.requestId()));
	}

	@PostMapping("/debit")
	public BalanceResponse debit(@PathVariable long playerId, @RequestBody AmountRequest request) {
		ChangeRequest change = parse(playerId, request.amount(), request.requestId(), request.reason());
		return new BalanceResponse(walletService.debit(playerId, change.amount(), change.reason(), change.requestId()));
	}

	@PostMapping("/refund")
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
}