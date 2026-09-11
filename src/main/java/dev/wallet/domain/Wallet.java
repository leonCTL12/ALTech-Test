package dev.wallet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "wallet")
public class Wallet {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "player_id", nullable = false, unique = true)
	private Player player;

	@Column(name = "balance", nullable = false)
	private long balance;

	@Column(name = "version", nullable = false)
	private int version;

	public Wallet(Player player, long balance) {
		this.player = player;
		this.balance = balance;
	}

	protected Wallet() {
	}

	public Long getId() {
		return id;
	}

	public Player getPlayer() {
		return player;
	}

	public long getBalance() {
		return balance;
	}

	public int getVersion() {
		return version;
	}

	public long credit(long amount) {
		this.balance += amount;
		return balance;
	}

	public long debit(long amount) {
		this.balance -= amount;
		return balance;
	}
}