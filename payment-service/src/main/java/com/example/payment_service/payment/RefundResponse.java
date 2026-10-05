package com.example.payment_service.payment;

import java.math.BigDecimal;
import java.util.UUID;

public record RefundResponse(
		UUID paymentId,
		UUID bookingId,
		BigDecimal amount,
		PaymentType type,
		PaymentStatus status
) {
}
