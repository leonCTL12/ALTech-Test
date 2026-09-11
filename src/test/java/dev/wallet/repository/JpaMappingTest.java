package dev.wallet.repository;

import java.util.UUID;

import dev.wallet.domain.Direction;
import dev.wallet.domain.LedgerEntry;
import dev.wallet.domain.Player;
import dev.wallet.domain.Reason;
import dev.wallet.domain.ReasonKind;
import dev.wallet.domain.Wallet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class JpaMappingTest {

	@Autowired
	private PlayerRepository players;

	@Autowired
	private WalletRepository wallets;

	@Autowired
	private LedgerEntryRepository ledgerEntries;

	@Test
	void playerSavesAndReadsBack() {
		Player saved = players.saveAndFlush(new Player());

		Player found = players.findById(saved.getId()).orElseThrow();

		assertThat(found.getId()).isEqualTo(saved.getId());
	}

	@Test
	void walletSavesAndReadsBackWithItsPlayer() {
		Wallet saved = newWallet(1250L);

		Wallet found = wallets.findById(saved.getId()).orElseThrow();
		assertThat(found.getId()).isEqualTo(saved.getId());
		assertThat(found.getPlayer().getId()).isEqualTo(saved.getPlayer().getId());
		assertThat(found.getBalance()).isEqualTo(1250L);
	}

	@Test
	void ledgerEntrySavesAndReadsBackWithItsWalletAndReason() {
		Wallet wallet = newWallet(0L);
		Reason reason = new Reason(ReasonKind.MISSION_REWARD, "Completed level 3", 42L);
		String requestId = UUID.randomUUID().toString();

		LedgerEntry saved = ledgerEntries.saveAndFlush(
				new LedgerEntry(wallet, 100L, Direction.CREDIT, reason, requestId, null));

		LedgerEntry found = ledgerEntries.findById(saved.getId()).orElseThrow();
		assertThat(found.getWallet().getId()).isEqualTo(wallet.getId());
		assertThat(found.getAmount()).isEqualTo(100L);
		assertThat(found.getDirection()).isEqualTo(Direction.CREDIT);
		assertThat(found.getReason().getKind()).isEqualTo(ReasonKind.MISSION_REWARD);
		assertThat(found.getReason().getDescription()).isEqualTo("Completed level 3");
		assertThat(found.getReason().getReferenceId()).isEqualTo(42L);
		assertThat(found.getRequestId()).isEqualTo(requestId);
		assertThat(found.getOriginalDebitId()).isNull();
		assertThat(found.getCreatedAt()).isNotNull();
	}

	@Test
	void insertingTwoEntriesWithTheSameRequestIdFailsOnTheUniqueConstraint() {
		Wallet wallet = newWallet(0L);
		Reason reason = new Reason(ReasonKind.ADMIN, "test", null);
		String requestId = UUID.randomUUID().toString();
		ledgerEntries.saveAndFlush(new LedgerEntry(wallet, 100L, Direction.CREDIT, reason, requestId, null));

		assertThatThrownBy(() -> ledgerEntries.saveAndFlush(
				new LedgerEntry(wallet, 100L, Direction.CREDIT, reason, requestId, null)))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	private Wallet newWallet(long balance) {
		Player player = players.saveAndFlush(new Player());
		return wallets.saveAndFlush(new Wallet(player, balance));
	}
}
