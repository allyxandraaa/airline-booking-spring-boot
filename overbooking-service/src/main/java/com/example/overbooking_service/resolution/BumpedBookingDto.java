package com.example.overbooking_service.resolution;

import com.example.overbooking_service.client.dto.TicketClass;

import java.math.BigDecimal;
import java.util.UUID;

public record BumpedBookingDto(UUID bookingId, TicketClass ticketClass, BigDecimal amount, BumpOutcome outcome) {
}
