package com.example.booking_service.booking;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateBookingRequest(
		@NotNull UUID flightId,
		@NotNull UUID passengerId,
		@NotBlank String passengerName,
		@NotNull TicketClass ticketClass
) {
}
