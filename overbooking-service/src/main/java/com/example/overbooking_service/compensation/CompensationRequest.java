package com.example.overbooking_service.compensation;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record CompensationRequest(@NotEmpty List<@NotNull UUID> bookingIds) {
}
