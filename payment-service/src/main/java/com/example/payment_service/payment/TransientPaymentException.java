package com.example.payment_service.payment;

public class TransientPaymentException extends RuntimeException {

	public TransientPaymentException(String message) {
		super(message);
	}
}
