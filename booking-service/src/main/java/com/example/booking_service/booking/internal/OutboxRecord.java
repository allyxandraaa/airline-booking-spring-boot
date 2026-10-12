package com.example.booking_service.booking.internal;

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
@Table(name = "outbox")
class OutboxRecord implements Persistable<UUID> {

	@Id
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

	protected OutboxRecord() {
	}

	OutboxRecord(UUID id, String aggregateType, UUID aggregateId, String eventType, String payload, Instant createdAt) {
		this.id = id;
		this.aggregateType = aggregateType;
		this.aggregateId = aggregateId;
		this.eventType = eventType;
		this.payload = payload;
		this.createdAt = createdAt;
	}

	void markProcessed() {
		this.processed = true;
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

	String getEventType() {
		return eventType;
	}

	String getPayload() {
		return payload;
	}

	Instant getCreatedAt() {
		return createdAt;
	}

	boolean isProcessed() {
		return processed;
	}

	@PostLoad
	@PostPersist
	void markNotNew() {
		this.isNew = false;
	}
}
