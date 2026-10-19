package com.example.flight_service.flight;

import java.math.BigDecimal;
import java.util.UUID;

public interface FlightService {

	FlightDto create(CreateFlightRequest request);

	BigDecimal calculatePrice(UUID flightId, TicketClass ticketClass);

	ReservationDto reserveSeat(UUID flightId, TicketClass ticketClass);

	void releaseSeat(UUID reservationId);
}
