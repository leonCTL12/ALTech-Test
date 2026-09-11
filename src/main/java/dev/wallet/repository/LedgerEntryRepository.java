package dev.wallet.repository;

import java.util.List;
import java.util.Optional;

import dev.wallet.domain.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

	List<LedgerEntry> findByWalletIdOrderByIdAsc(Long walletId);

	Optional<LedgerEntry> findByRequestId(String requestId);

	Optional<LedgerEntry> findByOriginalDebitId(Long originalDebitId);
}