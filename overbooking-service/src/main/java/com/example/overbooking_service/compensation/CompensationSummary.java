package com.example.overbooking_service.compensation;

import java.math.BigDecimal;

public record CompensationSummary(int total, int succeeded, int declined, BigDecimal refundedAmount) {
}
