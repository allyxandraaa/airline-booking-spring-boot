package com.example.overbooking_service.client;

import com.example.grpc.booking.v1.Booking;
import com.example.grpc.booking.v1.BookingServiceGrpc;
import com.example.grpc.booking.v1.StreamBookingsRequest;
import com.example.overbooking_service.client.dto.BatchRequest;
import com.example.overbooking_service.client.dto.BookingDto;
import com.example.overbooking_service.grpc.MoneyMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
class GrpcBookingClient implements BookingClient {

	private static final String STATUS_PREFIX = "BOOKING_STATUS_";

	private final BookingServiceGrpc.BookingServiceBlockingStub bookingStub;

	GrpcBookingClient(BookingServiceGrpc.BookingServiceBlockingStub bookingStub) {
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

	private static BookingDto toDto(Booking booking) {
		return new BookingDto(
				UUID.fromString(booking.getId()),
				MoneyMapper.fromProto(booking.getPriceAtBooking()),
				booking.getStatus().name().replace(STATUS_PREFIX, ""));
	}
}
