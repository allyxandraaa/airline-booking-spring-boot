package com.example.overbooking_service.exception;

import java.util.UUID;

public class FlightNotFoundException extends RuntimeException {

	public FlightNotFoundException(UUID id) {
		super("Рейс не знайдено: " + id);
	}
}
