package com.example.flight_service.grpc;

import com.example.flight_service.exception.FlightNotFoundException;
import com.example.flight_service.exception.ReservationNotFoundException;
import com.example.flight_service.exception.SeatUnavailableException;
import com.google.protobuf.Any;
import com.google.rpc.Code;
import com.google.rpc.ErrorInfo;
import io.grpc.StatusException;
import io.grpc.protobuf.StatusProto;
import org.springframework.grpc.server.exception.GrpcExceptionHandler;
import org.springframework.stereotype.Component;

@Component
class FlightGrpcExceptionHandler implements GrpcExceptionHandler {

	private static final String DOMAIN = "flight-service";

	@Override
	public StatusException handleException(Throwable exception) {
		return switch (exception) {
			case FlightNotFoundException e -> error(Code.NOT_FOUND, e.getMessage(), "FLIGHT_NOT_FOUND");
			case ReservationNotFoundException e -> error(Code.NOT_FOUND, e.getMessage(), "RESERVATION_NOT_FOUND");
			case SeatUnavailableException e -> error(Code.FAILED_PRECONDITION, e.getMessage(), "SEATS_UNAVAILABLE");
			default -> null;
		};
	}

	private static StatusException error(Code code, String message, String reason) {
		ErrorInfo info = ErrorInfo.newBuilder().setReason(reason).setDomain(DOMAIN).build();
		com.google.rpc.Status status = com.google.rpc.Status.newBuilder()
				.setCode(code.getNumber())
				.setMessage(message)
				.addDetails(Any.pack(info))
				.build();
		return StatusProto.toStatusException(status);
	}
}
