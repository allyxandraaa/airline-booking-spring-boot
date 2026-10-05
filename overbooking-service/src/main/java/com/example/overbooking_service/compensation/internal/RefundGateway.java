package com.example.overbooking_service.compensation.internal;

import com.example.overbooking_service.compensation.CompensationResult;

import java.math.BigDecimal;
import java.util.UUID;

interface RefundGateway {

	CompensationResult refund(UUID bookingId, BigDecimal amount, String idempotencyKey);
}
