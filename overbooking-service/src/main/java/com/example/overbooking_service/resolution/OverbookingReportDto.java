package com.example.overbooking_service.resolution;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OverbookingReportDto(
		UUID id,
		UUID flightId,
		int seatsEconomy,
		int seatsBusiness,
		int bumpedCount,
		BigDecimal totalCompensation,
		Instant createdAt,
		List<BumpedBookingDto> bumpedBookings
) {
}
