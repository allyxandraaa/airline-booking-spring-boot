package com.example.payment_service.payment;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentDto(
		UUID paymentId,
		UUID bookingId,
		UUID paymentMethodId,
		BigDecimal amount,
		PaymentType type,
		PaymentStatus status
) {
}
