package com.example.booking_service.grpc;

import com.example.booking_service.booking.BookingDto;
import com.example.booking_service.booking.BookingService;
import com.example.booking_service.booking.BookingStatus;
import com.example.booking_service.booking.TicketClass;
import com.example.grpc.booking.v1.Booking;
import com.example.grpc.booking.v1.BookingServiceGrpc;
import com.example.grpc.booking.v1.BumpBookingRequest;
import com.example.grpc.booking.v1.BumpBookingResponse;
import com.example.grpc.booking.v1.StreamBookingsRequest;
import com.example.grpc.booking.v1.StreamConfirmedBookingsRequest;
import com.google.protobuf.Timestamp;
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

	@Override
	public void streamConfirmedBookings(StreamConfirmedBookingsRequest request, StreamObserver<Booking> responseObserver) {
		for (BookingDto booking : bookingService.findConfirmedByFlight(parseId(request.getFlightId()))) {
			if (Context.current().isCancelled()) {
				return;
			}
			responseObserver.onNext(toProto(booking));
		}
		responseObserver.onCompleted();
	}

	@Override
	public void bumpBooking(BumpBookingRequest request, StreamObserver<BumpBookingResponse> responseObserver) {
		bookingService.bump(parseId(request.getBookingId()));
		responseObserver.onNext(BumpBookingResponse.getDefaultInstance());
		responseObserver.onCompleted();
	}

	private static Booking toProto(BookingDto booking) {
		Booking.Builder builder = Booking.newBuilder()
				.setId(booking.id().toString())
				.setStatus(toProto(booking.status()))
				.setFlightId(booking.flightId().toString())
				.setTicketClass(toProto(booking.ticketClass()))
				.setPurchaseTime(Timestamp.newBuilder()
						.setSeconds(booking.purchaseTime().getEpochSecond())
						.setNanos(booking.purchaseTime().getNano())
						.build());
		if (booking.priceAtBooking() != null) {
			builder.setPriceAtBooking(MoneyMapper.toProto(booking.priceAtBooking()));
		}
		return builder.build();
	}

	private static com.example.grpc.flight.v1.TicketClass toProto(TicketClass ticketClass) {
		return switch (ticketClass) {
			case ECONOMY -> com.example.grpc.flight.v1.TicketClass.TICKET_CLASS_ECONOMY;
			case BUSINESS -> com.example.grpc.flight.v1.TicketClass.TICKET_CLASS_BUSINESS;
		};
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
		} catch (IllegalArgumentException _) {
			throw invalidArgument("Некоректний ідентифікатор: " + value);
		}
	}

	private static StatusRuntimeException invalidArgument(String message) {
		return Status.INVALID_ARGUMENT.withDescription(message).asRuntimeException();
	}
}
