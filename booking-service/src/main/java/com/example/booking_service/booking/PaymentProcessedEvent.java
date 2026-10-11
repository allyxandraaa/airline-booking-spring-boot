package com.example.booking_service.booking;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record PaymentProcessedEvent(
		UUID eventId,
		UUID bookingId,
		UUID paymentId,
		PaymentProcessedStatus status,
		String reason,
		Instant occurredAt
) {

	public PaymentProcessedEvent {
		Objects.requireNonNull(eventId, "eventId must not be null");
		Objects.requireNonNull(bookingId, "bookingId must not be null");
		Objects.requireNonNull(status, "status must not be null");
		Objects.requireNonNull(occurredAt, "occurredAt must not be null");
	}

	public static PaymentProcessedEvent now(UUID bookingId, UUID paymentId, PaymentProcessedStatus status, String reason) {
		return new PaymentProcessedEvent(UUID.randomUUID(), bookingId, paymentId, status, reason, Instant.now());
	}
}
