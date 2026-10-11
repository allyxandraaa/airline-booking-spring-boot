package com.example.booking_service.booking;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record PaymentResultEvent(
		UUID eventId,
		UUID bookingId,
		UUID paymentId,
		PaymentResultStatus status,
		String reason,
		Instant occurredAt
) {

	public PaymentResultEvent {
		Objects.requireNonNull(eventId, "eventId must not be null");
		Objects.requireNonNull(bookingId, "bookingId must not be null");
		Objects.requireNonNull(status, "status must not be null");
		Objects.requireNonNull(occurredAt, "occurredAt must not be null");
	}

	public static PaymentResultEvent now(UUID bookingId, UUID paymentId, PaymentResultStatus status, String reason) {
		return new PaymentResultEvent(UUID.randomUUID(), bookingId, paymentId, status, reason, Instant.now());
	}
}
