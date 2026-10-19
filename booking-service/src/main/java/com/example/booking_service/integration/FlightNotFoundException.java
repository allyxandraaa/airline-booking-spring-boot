package com.example.booking_service.integration;

import java.util.UUID;

public class FlightNotFoundException extends RuntimeException {

	public FlightNotFoundException(UUID flightId) {
		super("Рейс не знайдено: " + flightId);
	}
}
