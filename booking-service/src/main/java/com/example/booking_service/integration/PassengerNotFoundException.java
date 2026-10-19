package com.example.booking_service.integration;

import java.util.UUID;

public class PassengerNotFoundException extends RuntimeException {

	public PassengerNotFoundException(UUID passengerId) {
		super("Пасажира не знайдено: " + passengerId);
	}
}
