package com.example.booking_service.integration;

import com.example.booking_service.booking.TicketClass;

import java.math.BigDecimal;
import java.util.UUID;

public interface FlightGateway {

	BigDecimal getPrice(UUID flightId, TicketClass ticketClass);

	UUID reserveSeat(UUID flightId, TicketClass ticketClass);

	void releaseSeat(UUID reservationId);
}
