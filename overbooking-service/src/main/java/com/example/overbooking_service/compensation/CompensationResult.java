package com.example.overbooking_service.compensation;

import java.math.BigDecimal;
import java.util.UUID;

public record CompensationResult(UUID bookingId, BigDecimal amount, String status) {
}
