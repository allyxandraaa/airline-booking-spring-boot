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
@Table(name = "processed_events")
class ProcessedEvent implements Persistable<UUID> {

	@Id
	@Column(nullable = false, updatable = false)
	private UUID eventId;

	@Column(nullable = false)
	private Instant processedAt;

	@Transient
	private boolean isNew = true;

	protected ProcessedEvent() {
	}

	ProcessedEvent(UUID eventId, Instant processedAt) {
		this.eventId = eventId;
		this.processedAt = processedAt;
	}

	@Override
	public UUID getId() {
		return eventId;
	}

	@Override
	public boolean isNew() {
		return isNew;
	}

	@PostLoad
	@PostPersist
	void markNotNew() {
		this.isNew = false;
	}
}
