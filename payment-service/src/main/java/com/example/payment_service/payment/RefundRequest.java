package com.example.payment_service.payment;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public record RefundRequest(
		@NotNull UUID bookingId,
		@NotNull @Positive BigDecimal amount
) {
}
