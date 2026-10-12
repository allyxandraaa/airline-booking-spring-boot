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
import java.util.UUID;

@Entity
@Table(name = "outbox_events")
class OutboxEvent implements Persistable<UUID> {

	@Id
	@Column(nullable = false, updatable = false)
	private UUID id;

	@Column(nullable = false)
	private String aggregateType;

	@Column(nullable = false)
	private UUID aggregateId;

	@Column(nullable = false)
	private String eventType;

	@Column(nullable = false, length = 4000)
	private String payload;

	@Column(nullable = false)
	private Instant createdAt;

	@Column(nullable = false)
	private boolean processed;

	@Transient
	private boolean isNew = true;

	protected OutboxEvent() {
	}

	OutboxEvent(UUID id, String aggregateType, UUID aggregateId, String eventType, String payload, Instant createdAt) {
		this.id = id;
		this.aggregateType = aggregateType;
		this.aggregateId = aggregateId;
		this.eventType = eventType;
		this.payload = payload;
		this.createdAt = createdAt;
	}

	@Override
	public UUID getId() {
		return id;
	}

	@Override
	public boolean isNew() {
		return isNew;
	}

	UUID getAggregateId() {
		return aggregateId;
	}

	String getPayload() {
		return payload;
	}

	boolean isProcessed() {
		return processed;
	}

	void markProcessed() {
		this.processed = true;
	}

	@PostLoad
	@PostPersist
	void markNotNew() {
		this.isNew = false;
	}
}
