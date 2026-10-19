package com.example.flight_service.exception;

import java.util.UUID;

public class FlightNotFoundException extends RuntimeException {

	public FlightNotFoundException(UUID id) {
		super("Рейс не знайдено: " + id);
	}
}
