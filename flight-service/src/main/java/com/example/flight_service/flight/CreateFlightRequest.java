package com.example.flight_service.flight;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.Instant;

public record CreateFlightRequest(
		@NotBlank String flightNumber,
		@NotBlank String origin,
		@NotBlank String destination,
		@NotNull @Future Instant departureTime,
		@Positive int totalSeatsEconomy,
		@Positive int totalSeatsBusiness,
		@NotNull @Positive BigDecimal basePriceEconomy,
		@NotNull @Positive BigDecimal basePriceBusiness,
		@PositiveOrZero int overbookingLimitPercent
) {
}
