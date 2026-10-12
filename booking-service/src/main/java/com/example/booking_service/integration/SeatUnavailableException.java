package com.example.booking_service.integration;

public class SeatUnavailableException extends RuntimeException {

	public SeatUnavailableException(String message) {
		super(message);
	}
}
