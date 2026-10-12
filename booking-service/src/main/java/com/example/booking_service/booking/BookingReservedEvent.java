package com.example.booking_service.booking;

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
		Objects.requireNonNull(eventId, "Поле eventId є обов'язковим");
		Objects.requireNonNull(bookingId, "Поле bookingId є обов'язковим");
		Objects.requireNonNull(paymentMethodId, "Поле paymentMethodId є обов'язковим");
		Objects.requireNonNull(amount, "Поле amount є обов'язковим");
		Objects.requireNonNull(occurredAt, "Поле occurredAt є обов'язковим");
		if (amount.signum() <= 0) {
			throw new IllegalArgumentException("Сума має бути більшою за нуль");
		}
	}

	public static BookingReservedEvent now(UUID bookingId, UUID paymentMethodId, BigDecimal amount) {
		return new BookingReservedEvent(UUID.randomUUID(), bookingId, paymentMethodId, amount, Instant.now());
	}
}
