package com.example.customer_service.exception;

import java.util.UUID;

public class PassengerNotFoundException extends RuntimeException {

	public PassengerNotFoundException(UUID id) {
		super("Пасажира не знайдено: " + id);
	}
}
