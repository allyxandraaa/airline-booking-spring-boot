package com.example.booking_service.exception;

public class BookingProcessingException extends RuntimeException {

	public BookingProcessingException(String message, Throwable cause) {
		super(message, cause);
	}
}
