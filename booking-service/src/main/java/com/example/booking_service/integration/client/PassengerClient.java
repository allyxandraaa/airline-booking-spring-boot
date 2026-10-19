package com.example.booking_service.integration.client;

import com.example.booking_service.integration.client.dto.PaymentMethodResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;

import java.util.UUID;

public interface PassengerClient {

	@GetExchange("/api/v1/passengers/{id}/payment-method")
	PaymentMethodResponse getPaymentMethod(@PathVariable UUID id);
}
