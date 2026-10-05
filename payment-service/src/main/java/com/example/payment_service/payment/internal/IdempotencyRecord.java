package com.example.payment_service.payment.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

import java.time.Instant;

@Entity
@Table(name = "idempotency_records")
class IdempotencyRecord implements Persistable<String> {

	@Id
	@Column(name = "idempotency_key", nullable = false, updatable = false)
	private String key;

	@Column(nullable = false, length = 4000)
	private String responseBody;

	@Column(nullable = false)
	private Instant createdAt;

	@Transient
	private boolean isNew = true;

	protected IdempotencyRecord() {
	}

	IdempotencyRecord(String key, String responseBody, Instant createdAt) {
		this.key = key;
		this.responseBody = responseBody;
		this.createdAt = createdAt;
	}

	@Override
	public String getId() {
		return key;
	}

	@Override
	public boolean isNew() {
		return isNew;
	}

	String getResponseBody() {
		return responseBody;
	}

	Instant getCreatedAt() {
		return createdAt;
	}

	@PostLoad
	@PostPersist
	void markNotNew() {
		this.isNew = false;
	}
}
