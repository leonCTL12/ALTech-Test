package dev.wallet.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "ledger_entry")
public class LedgerEntry {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "wallet_id", nullable = false)
	private Wallet wallet;

	@Column(name = "amount", nullable = false)
	private long amount;

	@Enumerated(EnumType.STRING)
	@Column(name = "direction", length = 16, nullable = false)
	private Direction direction;

	@Embedded
	private Reason reason;

	@Column(name = "request_id", length = 36, nullable = false, unique = true)
	private String requestId;

	@Column(name = "original_debit_id", unique = true)
	private Long originalDebitId;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	public LedgerEntry(Wallet wallet, long amount, Direction direction, Reason reason,
			String requestId, Long originalDebitId) {
		this.wallet = wallet;
		this.amount = amount;
		this.direction = direction;
		this.reason = reason;
		this.requestId = requestId;
		this.originalDebitId = originalDebitId;
	}

	protected LedgerEntry() {
	}

	@PrePersist
	void prePersist() {
		if (createdAt == null) {
			createdAt = Instant.now();
		}
	}

	public Long getId() {
		return id;
	}

	public Wallet getWallet() {
		return wallet;
	}

	public long getAmount() {
		return amount;
	}

	public Direction getDirection() {
		return direction;
	}

	public Reason getReason() {
		return reason;
	}

	public String getRequestId() {
		return requestId;
	}

	public Long getOriginalDebitId() {
		return originalDebitId;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}