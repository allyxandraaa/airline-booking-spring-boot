package com.example.payment_service.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record BookingReservedEvent(
		UUID eventId,
		UUID bookingId,
		UUID paymentMethodId,
		BigDecimal amount,
		Instant occurredAt
) {

	public BookingReservedEvent {
		Objects.requireNonNull(eventId, "eventId must not be null");
		Objects.requireNonNull(bookingId, "bookingId must not be null");
		Objects.requireNonNull(paymentMethodId, "paymentMethodId must not be null");
		Objects.requireNonNull(amount, "amount must not be null");
		Objects.requireNonNull(occurredAt, "occurredAt must not be null");
		if (amount.signum() <= 0) {
			throw new IllegalArgumentException("amount must be positive");
		}
	}

	public static BookingReservedEvent now(UUID bookingId, UUID paymentMethodId, BigDecimal amount) {
		return new BookingReservedEvent(UUID.randomUUID(), bookingId, paymentMethodId, amount, Instant.now());
	}
}
