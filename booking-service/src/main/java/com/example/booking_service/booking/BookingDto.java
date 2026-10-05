package com.example.booking_service.booking;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record BookingDto(
		UUID id,
		UUID flightId,
		UUID passengerId,
		BookingStatus status,
		BigDecimal priceAtBooking,
		Instant purchaseTime
) {
}
