package com.example.overbooking_service.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RefundRequest(UUID bookingId, BigDecimal amount) {
}
