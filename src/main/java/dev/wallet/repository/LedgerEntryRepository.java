package dev.wallet.repository;

import java.util.List;
import java.util.Optional;

import dev.wallet.domain.LedgerEntry;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

	List<LedgerEntry> findByWalletIdOrderByIdAsc(Long walletId);

	List<LedgerEntry> findByWalletIdOrderByIdDesc(Long walletId, Pageable pageable);

	List<LedgerEntry> findByWalletIdAndIdLessThanOrderByIdDesc(Long walletId, Long after, Pageable pageable);

	Optional<LedgerEntry> findByWalletIdAndRequestId(Long walletId, String requestId);

	Optional<LedgerEntry> findByOriginalLedgerEntryId(Long originalLedgerEntryId);
}