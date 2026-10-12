package com.example.payment_service.payment;

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
		Objects.requireNonNull(eventId, "Поле eventId є обов'язковим");
		Objects.requireNonNull(bookingId, "Поле bookingId є обов'язковим");
		Objects.requireNonNull(status, "Поле status є обов'язковим");
		Objects.requireNonNull(occurredAt, "Поле occurredAt є обов'язковим");
	}

	public static PaymentProcessedEvent now(UUID bookingId, UUID paymentId, PaymentProcessedStatus status, String reason) {
		return new PaymentProcessedEvent(UUID.randomUUID(), bookingId, paymentId, status, reason, Instant.now());
	}
}
