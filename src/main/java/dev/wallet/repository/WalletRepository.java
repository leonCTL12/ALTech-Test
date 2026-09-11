package dev.wallet.repository;

import java.util.Optional;

import dev.wallet.domain.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

public interface WalletRepository extends JpaRepository<Wallet, Long> {

	Optional<Wallet> findByPlayerId(Long playerId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select w from Wallet w where w.player.id = :playerId")
	Optional<Wallet> findByPlayerIdForUpdate(Long playerId);
}