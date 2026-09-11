package dev.wallet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

@Embeddable
public class Reason {

	@Enumerated(EnumType.STRING)
	@Column(name = "reason_kind", length = 32, nullable = false)
	private ReasonKind kind;

	@Column(name = "description", length = 255, nullable = false)
	private String description;

	protected Reason() {
	}

	public Reason(ReasonKind kind, String description) {
		this.kind = kind;
		this.description = description;
	}

	public ReasonKind getKind() {
		return kind;
	}

	public String getDescription() {
		return description;
	}
}