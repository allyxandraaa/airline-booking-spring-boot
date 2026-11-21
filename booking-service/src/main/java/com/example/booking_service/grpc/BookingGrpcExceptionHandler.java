package com.example.booking_service.grpc;

import com.example.booking_service.exception.BookingNotBumpableException;
import com.example.booking_service.exception.BookingNotFoundException;
import com.google.protobuf.Any;
import com.google.rpc.Code;
import com.google.rpc.ErrorInfo;
import io.grpc.StatusException;
import io.grpc.protobuf.StatusProto;
import org.springframework.grpc.server.exception.GrpcExceptionHandler;
import org.springframework.stereotype.Component;

@Component
class BookingGrpcExceptionHandler implements GrpcExceptionHandler {

	private static final String DOMAIN = "booking-service";

	@Override
	public StatusException handleException(Throwable exception) {
		return switch (exception) {
			case BookingNotFoundException e -> error(Code.NOT_FOUND, e.getMessage(), "BOOKING_NOT_FOUND");
			case BookingNotBumpableException e -> error(Code.FAILED_PRECONDITION, e.getMessage(), "BOOKING_NOT_BUMPABLE");
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
