package com.example.flight_service.exception;

import com.example.flight_service.flight.TicketClass;

import java.util.UUID;

public class SeatUnavailableException extends RuntimeException {

	public SeatUnavailableException(UUID flightId, TicketClass ticketClass) {
		super("Немає вільних місць на рейсі " + flightId + " для класу " + ticketClass);
	}
}
