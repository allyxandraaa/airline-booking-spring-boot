package com.example.booking_service.integration;

import com.example.booking_service.booking.TicketClass;
import com.example.booking_service.integration.client.FlightClient;
import com.example.booking_service.integration.client.dto.ReserveSeatRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;

import java.math.BigDecimal;
import java.util.UUID;

@Component
class FlightGatewayImpl implements FlightGateway {

	private final FlightClient flightClient;

	FlightGatewayImpl(FlightClient flightClient) {
		this.flightClient = flightClient;
	}

	@Override
	public BigDecimal getPrice(UUID flightId, TicketClass ticketClass) {
		try {
			return flightClient.getPrice(flightId, ticketClass).price();
		} catch (HttpClientErrorException.NotFound e) {
			throw new FlightNotFoundException(flightId);
		}
	}

	@Override
	public UUID reserveSeat(UUID flightId, TicketClass ticketClass) {
		try {
			return flightClient.reserveSeat(flightId, new ReserveSeatRequest(ticketClass)).reservationId();
		} catch (HttpClientErrorException.NotFound e) {
			throw new FlightNotFoundException(flightId);
		} catch (HttpClientErrorException.Conflict e) {
			throw new SeatUnavailableException("Немає вільних місць на рейсі " + flightId);
		}
	}

	@Override
	public void releaseSeat(UUID reservationId) {
		flightClient.releaseSeat(reservationId);
	}
}
