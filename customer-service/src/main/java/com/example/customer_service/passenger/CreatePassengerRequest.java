package com.example.customer_service.passenger;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record CreatePassengerRequest(
		@NotBlank String fullName,
		@NotBlank @Email String email,
		@NotBlank String phone,
		@NotBlank String documentNumber
) {
}
