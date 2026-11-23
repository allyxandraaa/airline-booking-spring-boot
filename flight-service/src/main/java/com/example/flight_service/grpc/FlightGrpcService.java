package com.example.flight_service.grpc;

import com.example.flight_service.flight.FlightCapacityDto;
import com.example.flight_service.flight.FlightService;
import com.example.flight_service.flight.ReservationDto;
import com.example.flight_service.flight.TicketClass;
import com.example.grpc.flight.v1.FlightServiceGrpc;
import com.example.grpc.flight.v1.GetFlightCapacityRequest;
import com.example.grpc.flight.v1.GetFlightCapacityResponse;
import com.example.grpc.flight.v1.GetPriceRequest;
import com.example.grpc.flight.v1.GetPriceResponse;
import com.example.grpc.flight.v1.ReleaseSeatRequest;
import com.example.grpc.flight.v1.ReleaseSeatResponse;
import com.example.grpc.flight.v1.ReserveSeatRequest;
import com.example.grpc.flight.v1.ReserveSeatResponse;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.springframework.grpc.server.service.GrpcService;

import java.math.BigDecimal;
import java.util.UUID;

@GrpcService
class FlightGrpcService extends FlightServiceGrpc.FlightServiceImplBase {

	private final FlightService flightService;

	FlightGrpcService(FlightService flightService) {
		this.flightService = flightService;
	}

	@Override
	public void getPrice(GetPriceRequest request, StreamObserver<GetPriceResponse> responseObserver) {
		BigDecimal price = flightService.calculatePrice(parseId(request.getFlightId()), toDomain(request.getTicketClass()));
		responseObserver.onNext(GetPriceResponse.newBuilder().setPrice(MoneyMapper.toProto(price)).build());
		responseObserver.onCompleted();
	}

	@Override
	public void reserveSeat(ReserveSeatRequest request, StreamObserver<ReserveSeatResponse> responseObserver) {
		ReservationDto reservation = flightService.reserveSeat(parseId(request.getFlightId()), toDomain(request.getTicketClass()));
		responseObserver.onNext(ReserveSeatResponse.newBuilder()
				.setReservationId(reservation.reservationId().toString())
				.build());
		responseObserver.onCompleted();
	}

	@Override
	public void releaseSeat(ReleaseSeatRequest request, StreamObserver<ReleaseSeatResponse> responseObserver) {
		flightService.releaseSeat(parseId(request.getReservationId()));
		responseObserver.onNext(ReleaseSeatResponse.getDefaultInstance());
		responseObserver.onCompleted();
	}

	@Override
	public void getFlightCapacity(GetFlightCapacityRequest request, StreamObserver<GetFlightCapacityResponse> responseObserver) {
		FlightCapacityDto capacity = flightService.getCapacity(parseId(request.getFlightId()));
		responseObserver.onNext(GetFlightCapacityResponse.newBuilder()
				.setSeatsEconomy(capacity.seatsEconomy())
				.setSeatsBusiness(capacity.seatsBusiness())
				.build());
		responseObserver.onCompleted();
	}

	private static UUID parseId(String value) {
		try {
			return UUID.fromString(value);
		} catch (IllegalArgumentException _) {
			throw invalidArgument("Некоректний ідентифікатор: " + value);
		}
	}

	private static TicketClass toDomain(com.example.grpc.flight.v1.TicketClass ticketClass) {
		return switch (ticketClass) {
			case TICKET_CLASS_ECONOMY -> TicketClass.ECONOMY;
			case TICKET_CLASS_BUSINESS -> TicketClass.BUSINESS;
			default -> throw invalidArgument("Не вказано клас квитка");
		};
	}

	private static StatusRuntimeException invalidArgument(String message) {
		return Status.INVALID_ARGUMENT.withDescription(message).asRuntimeException();
	}
}
