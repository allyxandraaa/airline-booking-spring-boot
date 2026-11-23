package com.example.booking_service.exception;

import com.example.booking_service.booking.BookingStatus;

import java.util.UUID;

public class BookingNotBumpableException extends RuntimeException {

	public BookingNotBumpableException(UUID id, BookingStatus status) {
		super("Бронювання " + id + " зі статусом " + status + " не можна зняти з рейсу");
	}
}
