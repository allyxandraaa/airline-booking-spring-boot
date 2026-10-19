package com.example.flight_service.flight;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record FlightDto(
		UUID id,
		String flightNumber,
		String origin,
		String destination,
		Instant departureTime,
		int totalSeatsEconomy,
		int totalSeatsBusiness,
		BigDecimal basePriceEconomy,
		BigDecimal basePriceBusiness,
		int overbookingLimitPercent
) {
}
