package com.example.booking_service.booking;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record BookingDto(
		UUID id,
		UUID flightId,
		UUID passengerId,
		String passengerName,
		TicketClass ticketClass,
		BookingStatus status,
		BigDecimal priceAtBooking,
		UUID reservationId,
		UUID paymentId,
		Instant purchaseTime
) {
}
