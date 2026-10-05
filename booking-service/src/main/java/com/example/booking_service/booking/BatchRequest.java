package com.example.booking_service.booking;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record BatchRequest(@NotEmpty List<@NotNull UUID> ids) {
}
