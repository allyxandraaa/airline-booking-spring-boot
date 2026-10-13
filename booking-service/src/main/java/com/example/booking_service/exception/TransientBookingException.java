package com.example.booking_service.exception;

public class TransientBookingException extends RuntimeException {

	public TransientBookingException(String message, Throwable cause) {
		super(message, cause);
	}
}
