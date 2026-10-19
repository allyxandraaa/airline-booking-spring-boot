package com.example.customer_service.passenger;

import java.util.UUID;

public record PassengerDto(
		UUID id,
		String fullName,
		String email,
		String phone,
		String documentNumber,
		UUID savedPaymentMethodId
) {
}
