package com.example.overbooking_service.client;

import com.example.grpc.booking.v1.Booking;
import com.example.grpc.booking.v1.BookingServiceGrpc;
import com.example.grpc.booking.v1.BumpBookingRequest;
import com.example.grpc.booking.v1.StreamBookingsRequest;
import com.example.grpc.booking.v1.StreamConfirmedBookingsRequest;
import com.example.overbooking_service.client.dto.BatchRequest;
import com.example.overbooking_service.client.dto.BookingDto;
import com.example.overbooking_service.client.dto.ConfirmedBookingDto;
import com.example.overbooking_service.client.dto.TicketClass;
import com.example.overbooking_service.grpc.MoneyMapper;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
class BookingGatewayImpl implements BookingGateway {

	private static final String STATUS_PREFIX = "BOOKING_STATUS_";

	private final BookingServiceGrpc.BookingServiceBlockingStub bookingStub;

	BookingGatewayImpl(BookingServiceGrpc.BookingServiceBlockingStub bookingStub) {
		this.bookingStub = bookingStub;
	}

	@Override
	public List<BookingDto> getBookingsByIds(BatchRequest request) {
		StreamBookingsRequest grpcRequest = StreamBookingsRequest.newBuilder()
				.addAllBookingIds(request.ids().stream().map(UUID::toString).toList())
				.build();
		List<BookingDto> bookings = new ArrayList<>();
		bookingStub.streamBookings(grpcRequest).forEachRemaining(booking -> bookings.add(toDto(booking)));
		return bookings;
	}

	@Override
	public List<ConfirmedBookingDto> getConfirmedBookings(UUID flightId) {
		StreamConfirmedBookingsRequest grpcRequest = StreamConfirmedBookingsRequest.newBuilder()
				.setFlightId(flightId.toString())
				.build();
		List<ConfirmedBookingDto> bookings = new ArrayList<>();
		bookingStub.streamConfirmedBookings(grpcRequest)
				.forEachRemaining(booking -> bookings.add(toConfirmedDto(booking)));
		return bookings;
	}

	@Override
	public void bump(UUID bookingId) {
		bookingStub.bumpBooking(BumpBookingRequest.newBuilder().setBookingId(bookingId.toString()).build());
	}

	private static BookingDto toDto(Booking booking) {
		return new BookingDto(
				UUID.fromString(booking.getId()),
				MoneyMapper.fromProto(booking.getPriceAtBooking()),
				booking.getStatus().name().replace(STATUS_PREFIX, ""));
	}

	private static ConfirmedBookingDto toConfirmedDto(Booking booking) {
		return new ConfirmedBookingDto(
				UUID.fromString(booking.getId()),
				toTicketClass(booking.getTicketClass()),
				MoneyMapper.fromProto(booking.getPriceAtBooking()),
				Instant.ofEpochSecond(booking.getPurchaseTime().getSeconds(), booking.getPurchaseTime().getNanos()));
	}

	private static TicketClass toTicketClass(com.example.grpc.flight.v1.TicketClass ticketClass) {
		return switch (ticketClass) {
			case TICKET_CLASS_ECONOMY -> TicketClass.ECONOMY;
			case TICKET_CLASS_BUSINESS -> TicketClass.BUSINESS;
			default -> throw new IllegalStateException("Невідомий клас квитка: " + ticketClass);
		};
	}
}
