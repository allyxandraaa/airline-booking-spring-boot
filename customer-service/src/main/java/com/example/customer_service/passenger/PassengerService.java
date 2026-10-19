package com.example.customer_service.passenger;

import java.util.UUID;

public interface PassengerService {

	PassengerDto create(CreatePassengerRequest request);

	UUID getPaymentMethodId(UUID passengerId);
}
