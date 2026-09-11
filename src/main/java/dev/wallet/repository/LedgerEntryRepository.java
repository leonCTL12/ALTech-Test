package dev.wallet.repository;

import java.util.List;

import dev.wallet.domain.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

	List<LedgerEntry> findByWalletIdOrderByIdAsc(Long walletId);
}