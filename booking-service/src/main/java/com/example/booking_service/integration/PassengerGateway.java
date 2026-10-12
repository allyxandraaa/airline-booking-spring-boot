package com.example.booking_service.integration;

import java.util.UUID;

public interface PassengerGateway {

	UUID getPaymentMethodId(UUID passengerId);
}
