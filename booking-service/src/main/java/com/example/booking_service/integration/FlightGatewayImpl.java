package com.example.booking_service.integration;

import com.example.booking_service.booking.TicketClass;
import com.example.booking_service.grpc.MoneyMapper;
import com.example.grpc.flight.v1.FlightServiceGrpc;
import com.example.grpc.flight.v1.GetPriceRequest;
import com.example.grpc.flight.v1.ReleaseSeatRequest;
import com.example.grpc.flight.v1.ReserveSeatRequest;
import io.grpc.StatusRuntimeException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

@Component
class FlightGatewayImpl implements FlightGateway {

	private final FlightServiceGrpc.FlightServiceBlockingStub flightStub;

	FlightGatewayImpl(FlightServiceGrpc.FlightServiceBlockingStub flightStub) {
		this.flightStub = flightStub;
	}

	@Override
	public BigDecimal getPrice(UUID flightId, TicketClass ticketClass) {
		try {
			return MoneyMapper.fromProto(flightStub.getPrice(GetPriceRequest.newBuilder()
					.setFlightId(flightId.toString())
					.setTicketClass(toProto(ticketClass))
					.build()).getPrice());
		} catch (StatusRuntimeException e) {
			throw translate(e, flightId);
		}
	}

	@Override
	public UUID reserveSeat(UUID flightId, TicketClass ticketClass) {
		try {
			return UUID.fromString(flightStub.reserveSeat(ReserveSeatRequest.newBuilder()
					.setFlightId(flightId.toString())
					.setTicketClass(toProto(ticketClass))
					.build()).getReservationId());
		} catch (StatusRuntimeException e) {
			throw translate(e, flightId);
		}
	}

	@Override
	public void releaseSeat(UUID reservationId) {
		flightStub.releaseSeat(ReleaseSeatRequest.newBuilder().setReservationId(reservationId.toString()).build());
	}

	private static RuntimeException translate(StatusRuntimeException e, UUID flightId) {
		return switch (e.getStatus().getCode()) {
			case NOT_FOUND -> new FlightNotFoundException(flightId);
			case FAILED_PRECONDITION -> new SeatUnavailableException("Немає вільних місць на рейсі " + flightId);
			default -> e;
		};
	}

	private static com.example.grpc.flight.v1.TicketClass toProto(TicketClass ticketClass) {
		return switch (ticketClass) {
			case ECONOMY -> com.example.grpc.flight.v1.TicketClass.TICKET_CLASS_ECONOMY;
			case BUSINESS -> com.example.grpc.flight.v1.TicketClass.TICKET_CLASS_BUSINESS;
		};
	}
}
