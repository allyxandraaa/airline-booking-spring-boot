package com.example.flight_service.flight;

import jakarta.validation.constraints.NotNull;

public record ReserveSeatRequest(@NotNull TicketClass ticketClass) {
}
