package com.example.booking_service.booking;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record PaymentRequestedEvent(
		UUID eventId,
		UUID bookingId,
		UUID paymentMethodId,
		BigDecimal amount,
		Instant occurredAt
) {

	public PaymentRequestedEvent {
		Objects.requireNonNull(eventId, "eventId must not be null");
		Objects.requireNonNull(bookingId, "bookingId must not be null");
		Objects.requireNonNull(paymentMethodId, "paymentMethodId must not be null");
		Objects.requireNonNull(amount, "amount must not be null");
		Objects.requireNonNull(occurredAt, "occurredAt must not be null");
		if (amount.signum() <= 0) {
			throw new IllegalArgumentException("amount must be positive");
		}
	}

	public static PaymentRequestedEvent now(UUID bookingId, UUID paymentMethodId, BigDecimal amount) {
		return new PaymentRequestedEvent(UUID.randomUUID(), bookingId, paymentMethodId, amount, Instant.now());
	}
}
