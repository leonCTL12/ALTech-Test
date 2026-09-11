package dev.wallet.service;

import dev.wallet.api.error.ApiException;
import dev.wallet.domain.Direction;
import dev.wallet.domain.LedgerEntry;
import dev.wallet.domain.MinorUnits;
import dev.wallet.domain.Reason;
import dev.wallet.domain.Wallet;
import dev.wallet.repository.LedgerEntryRepository;
import dev.wallet.repository.WalletRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class WalletService {

	private final WalletRepository wallets;
	private final LedgerEntryRepository ledgerEntries;

	public WalletService(WalletRepository wallets, LedgerEntryRepository ledgerEntries) {
		this.wallets = wallets;
		this.ledgerEntries = ledgerEntries;
	}

	@Transactional
	public String credit(long playerId, MinorUnits amount, Reason reason, String requestId) {
		Wallet wallet = walletOrThrow(wallets.findByPlayerIdForUpdate(playerId), playerId);
		if (alreadyApplied(requestId)) {
			return MinorUnits.format(wallet.getBalance());
		}
		ledgerEntries.save(new LedgerEntry(wallet, amount.value(), Direction.CREDIT, reason, requestId, null));
		return MinorUnits.format(wallet.credit(amount.value()));
	}

	@Transactional
	public String debit(long playerId, MinorUnits amount, Reason reason, String requestId) {
		Wallet wallet = walletOrThrow(wallets.findByPlayerIdForUpdate(playerId), playerId);
		if (alreadyApplied(requestId)) {
			return MinorUnits.format(wallet.getBalance());
		}
		if (wallets.debitIfSufficient(wallet.getId(), amount.value()) == 0) {
			throw new ApiException(HttpStatus.CONFLICT, "insufficient_balance",
					"The balance of " + MinorUnits.format(wallet.getBalance())
							+ " is not enough to debit " + amount.toDecimalString() + ".");
		}
		ledgerEntries.save(new LedgerEntry(wallet, amount.value(), Direction.DEBIT, reason, requestId, null));
		return MinorUnits.format(wallet.debit(amount.value()));
	}

	@Transactional
	public String refund(long playerId, MinorUnits amount, Reason reason, String requestId, Long originalLedgerEntryId) {
		Wallet wallet = walletOrThrow(wallets.findByPlayerIdForUpdate(playerId), playerId);

		if (alreadyApplied(requestId)) {
			return MinorUnits.format(wallet.getBalance());
		}
		if (ledgerEntries.findByOriginalLedgerEntryId(originalLedgerEntryId).isPresent()) {
			throw new ApiException(HttpStatus.CONFLICT, "already_refunded",
					"The debit with id " + originalLedgerEntryId + " has already been refunded.");
		}
		LedgerEntry originalEntry = ledgerEntries.findById(originalLedgerEntryId).orElseThrow(() ->
				new ApiException(HttpStatus.NOT_FOUND, "debit_not_found",
						"No debit with id " + originalLedgerEntryId + " exists."));
		if (originalEntry.getDirection() != Direction.DEBIT) {
			throw new ApiException(HttpStatus.NOT_FOUND, "debit_not_found",
					"Ledger entry " + originalLedgerEntryId + " is not a debit.");
		}

		ledgerEntries.save(new LedgerEntry(wallet, amount.value(), Direction.CREDIT, reason, requestId, originalLedgerEntryId));
		return MinorUnits.format(wallet.credit(amount.value()));
	}

	public String getBalance(long playerId) {
		Wallet wallet = walletOrThrow(wallets.findByPlayerId(playerId), playerId);
		return MinorUnits.format(wallet.getBalance());
	}

	public LedgerHistory history(long playerId, Long after, int limit) {
		Wallet wallet = walletOrThrow(wallets.findByPlayerId(playerId), playerId);
		Pageable page = PageRequest.of(0, limit + 1);
		List<LedgerEntry> candidates = after == null
				? ledgerEntries.findByWalletIdOrderByIdDesc(wallet.getId(), page)
				: ledgerEntries.findByWalletIdAndIdLessThanOrderByIdDesc(wallet.getId(), after, page);
		boolean hasMore = candidates.size() > limit;
		List<LedgerEntry> entries = hasMore ? candidates.subList(0, limit) : candidates;
		Long nextAfter = hasMore ? entries.get(entries.size() - 1).getId() : null;
		return new LedgerHistory(entries, nextAfter);
	}

	public record LedgerHistory(List<LedgerEntry> entries, Long nextAfter) {
	}

	private boolean alreadyApplied(String requestId) {
		return ledgerEntries.findByRequestId(requestId).isPresent();
	}

	private Wallet walletOrThrow(Optional<Wallet> wallet, long playerId) {
		return wallet.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "player_not_found",
				"No player with id " + playerId + " exists."));
	}
}