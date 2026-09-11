package dev.wallet.repository;

import java.util.Optional;

import dev.wallet.domain.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WalletRepository extends JpaRepository<Wallet, Long> {

	Optional<Wallet> findByPlayerId(Long playerId);
}