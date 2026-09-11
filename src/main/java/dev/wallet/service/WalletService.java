package dev.wallet.service;

import dev.wallet.api.error.ApiException;
import dev.wallet.domain.Direction;
import dev.wallet.domain.LedgerEntry;
import dev.wallet.domain.MinorUnits;
import dev.wallet.domain.Reason;
import dev.wallet.domain.Wallet;
import dev.wallet.repository.LedgerEntryRepository;
import dev.wallet.repository.WalletRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
		ledgerEntries.save(new LedgerEntry(wallet, amount.value(), Direction.CREDIT, reason, requestId, null));
		return MinorUnits.format(wallet.credit(amount.value()));
	}

	public String getBalance(long playerId) {
		Wallet wallet = walletOrThrow(wallets.findByPlayerId(playerId), playerId);
		return MinorUnits.format(wallet.getBalance());
	}

	private Wallet walletOrThrow(Optional<Wallet> wallet, long playerId) {
		return wallet.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "player_not_found",
				"No player with id " + playerId + " exists."));
	}
}