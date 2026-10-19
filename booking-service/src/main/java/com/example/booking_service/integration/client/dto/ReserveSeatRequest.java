package com.example.booking_service.integration.client.dto;

import com.example.booking_service.booking.TicketClass;

public record ReserveSeatRequest(TicketClass ticketClass) {
}
