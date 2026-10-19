package com.example.flight_service.exception;

import java.util.UUID;

public class ReservationNotFoundException extends RuntimeException {

	public ReservationNotFoundException(UUID id) {
		super("Резервацію не знайдено: " + id);
	}
}
