package com.example.booking_service.integration;

import com.example.booking_service.integration.client.PassengerClient;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;

import java.util.UUID;

@Component
class PassengerGatewayImpl implements PassengerGateway {

	private final PassengerClient passengerClient;

	PassengerGatewayImpl(PassengerClient passengerClient) {
		this.passengerClient = passengerClient;
	}

	@Override
	public UUID getPaymentMethodId(UUID passengerId) {
		try {
			return passengerClient.getPaymentMethod(passengerId).paymentMethodId();
		} catch (HttpClientErrorException.NotFound e) {
			throw new PassengerNotFoundException(passengerId);
		}
	}
}
