package dev.wallet.service;

import dev.wallet.domain.Player;
import dev.wallet.domain.Wallet;
import dev.wallet.repository.PlayerRepository;
import dev.wallet.repository.WalletRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlayerService {

	private final PlayerRepository players;
	private final WalletRepository wallets;

	public PlayerService(PlayerRepository players, WalletRepository wallets) {
		this.players = players;
		this.wallets = wallets;
	}

	@Transactional
	public long createPlayer() {
		Player player = players.save(new Player());
		wallets.save(new Wallet(player, 0L));
		return player.getId();
	}
}