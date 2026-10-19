package com.example.customer_service.passenger.internal;

import com.example.customer_service.passenger.CreatePassengerRequest;
import com.example.customer_service.passenger.PassengerDto;
import com.example.customer_service.passenger.PassengerService;
import com.example.customer_service.passenger.PaymentMethodDto;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/passengers")
class PassengerController {

	private final PassengerService passengerService;

	PassengerController(PassengerService passengerService) {
		this.passengerService = passengerService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public PassengerDto create(@Valid @RequestBody CreatePassengerRequest request) {
		return passengerService.create(request);
	}

	@GetMapping("/{id}/payment-method")
	public PaymentMethodDto getPaymentMethod(@PathVariable UUID id) {
		return new PaymentMethodDto(passengerService.getPaymentMethodId(id));
	}
}
