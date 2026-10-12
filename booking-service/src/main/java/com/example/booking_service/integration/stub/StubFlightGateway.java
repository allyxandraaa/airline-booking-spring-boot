package com.example.booking_service.integration.stub;

import com.example.booking_service.booking.TicketClass;
import com.example.booking_service.integration.FlightGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

@Component
class StubFlightGateway implements FlightGateway {

	private static final Logger log = LoggerFactory.getLogger(StubFlightGateway.class);

	private final BigDecimal economyPrice;
	private final BigDecimal businessPrice;

	StubFlightGateway(@Value("${booking.stub.economy-price:120.00}") BigDecimal economyPrice,
					  @Value("${booking.stub.business-price:300.00}") BigDecimal businessPrice) {
		this.economyPrice = economyPrice;
		this.businessPrice = businessPrice;
	}

	@Override
	public BigDecimal getPrice(UUID flightId, TicketClass ticketClass) {
		return ticketClass == TicketClass.BUSINESS ? businessPrice : economyPrice;
	}

	@Override
	public UUID reserveSeat(UUID flightId, TicketClass ticketClass) {
		return UUID.randomUUID();
	}

	@Override
	public void releaseSeat(UUID reservationId) {
		log.info("Місце звільнено, reservationId={}", reservationId);
	}
}
