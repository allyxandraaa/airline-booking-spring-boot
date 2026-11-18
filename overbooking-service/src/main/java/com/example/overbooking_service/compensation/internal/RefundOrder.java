package com.example.overbooking_service.compensation.internal;

import java.math.BigDecimal;
import java.util.UUID;

record RefundOrder(UUID bookingId, BigDecimal amount, String idempotencyKey) {
}
