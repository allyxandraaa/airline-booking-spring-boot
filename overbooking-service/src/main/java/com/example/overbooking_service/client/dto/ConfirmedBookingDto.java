package com.example.overbooking_service.client.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ConfirmedBookingDto(UUID id, TicketClass ticketClass, BigDecimal priceAtBooking, Instant purchaseTime) {
}
