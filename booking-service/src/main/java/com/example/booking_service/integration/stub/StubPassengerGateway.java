package com.example.booking_service.integration.stub;

import com.example.booking_service.integration.PassengerGateway;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Component
class StubPassengerGateway implements PassengerGateway {

	@Override
	public UUID getPaymentMethodId(UUID passengerId) {
		return UUID.nameUUIDFromBytes(("payment-method:" + passengerId).getBytes(StandardCharsets.UTF_8));
	}
}
