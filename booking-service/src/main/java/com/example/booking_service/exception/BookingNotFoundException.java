package com.example.booking_service.exception;

import java.util.UUID;

public class BookingNotFoundException extends RuntimeException {

	public BookingNotFoundException(UUID id) {
		super("Бронювання не знайдено: " + id);
	}
}
