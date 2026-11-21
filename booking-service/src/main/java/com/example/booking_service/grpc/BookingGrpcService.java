package com.example.booking_service.grpc;

import com.example.booking_service.booking.BookingDto;
import com.example.booking_service.booking.BookingService;
import com.example.booking_service.booking.BookingStatus;
import com.example.grpc.booking.v1.Booking;
import com.example.grpc.booking.v1.BookingServiceGrpc;
import com.example.grpc.booking.v1.StreamBookingsRequest;
import io.grpc.Context;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.springframework.grpc.server.service.GrpcService;

import java.util.List;
import java.util.UUID;

@GrpcService
class BookingGrpcService extends BookingServiceGrpc.BookingServiceImplBase {

	private final BookingService bookingService;

	BookingGrpcService(BookingService bookingService) {
		this.bookingService = bookingService;
	}

	@Override
	public void streamBookings(StreamBookingsRequest request, StreamObserver<Booking> responseObserver) {
		if (request.getBookingIdsCount() == 0) {
			throw invalidArgument("Список бронювань порожній");
		}
		List<UUID> ids = request.getBookingIdsList().stream().map(BookingGrpcService::parseId).toList();
		for (BookingDto booking : bookingService.findByIds(ids)) {
			if (Context.current().isCancelled()) {
				return;
			}
			responseObserver.onNext(toProto(booking));
		}
		responseObserver.onCompleted();
	}

	private static Booking toProto(BookingDto booking) {
		Booking.Builder builder = Booking.newBuilder()
				.setId(booking.id().toString())
				.setStatus(toProto(booking.status()));
		if (booking.priceAtBooking() != null) {
			builder.setPriceAtBooking(MoneyMapper.toProto(booking.priceAtBooking()));
		}
		return builder.build();
	}

	private static com.example.grpc.booking.v1.BookingStatus toProto(BookingStatus status) {
		return switch (status) {
			case PENDING -> com.example.grpc.booking.v1.BookingStatus.BOOKING_STATUS_PENDING;
			case SEAT_RESERVED -> com.example.grpc.booking.v1.BookingStatus.BOOKING_STATUS_SEAT_RESERVED;
			case CONFIRMED -> com.example.grpc.booking.v1.BookingStatus.BOOKING_STATUS_CONFIRMED;
			case FAILED -> com.example.grpc.booking.v1.BookingStatus.BOOKING_STATUS_FAILED;
			case BUMPED -> com.example.grpc.booking.v1.BookingStatus.BOOKING_STATUS_BUMPED;
		};
	}

	private static UUID parseId(String value) {
		try {
			return UUID.fromString(value);
		} catch (IllegalArgumentException e) {
			throw invalidArgument("Некоректний ідентифікатор: " + value);
		}
	}

	private static StatusRuntimeException invalidArgument(String message) {
		return Status.INVALID_ARGUMENT.withDescription(message).asRuntimeException();
	}
}
