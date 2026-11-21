package com.example.booking_service.grpc;

import com.example.booking_service.booking.BookingDto;
import com.example.booking_service.booking.BookingService;
import com.example.booking_service.booking.BookingStatus;
import com.example.booking_service.booking.PaymentResultService;
import com.example.booking_service.booking.TicketClass;
import com.example.grpc.booking.v1.Booking;
import com.example.grpc.booking.v1.BookingServiceGrpc;
import com.example.grpc.booking.v1.StreamBookingsRequest;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.grpc.test.autoconfigure.AutoConfigureTestGrpcTransport;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.grpc.client.ImportGrpcClients;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureTestGrpcTransport
@ImportGrpcClients(types = BookingServiceGrpc.BookingServiceBlockingStub.class)
class BookingGrpcServiceTest {

	@Autowired
	BookingServiceGrpc.BookingServiceBlockingStub stub;

	@MockitoBean
	BookingService bookingService;

	@MockitoBean
	PaymentResultService paymentResultService;

	@Test
	void streamsEveryFoundBooking() {
		BookingDto first = booking(BookingStatus.CONFIRMED, new BigDecimal("129.50"));
		BookingDto second = booking(BookingStatus.SEAT_RESERVED, new BigDecimal("80"));
		BookingDto third = booking(BookingStatus.BUMPED, new BigDecimal("15.25"));
		List<UUID> ids = List.of(first.id(), second.id(), third.id());
		when(bookingService.findByIds(ids)).thenReturn(List.of(first, second, third));

		List<Booking> received = collect(request(ids));

		assertThat(received).extracting(Booking::getId)
				.containsExactly(first.id().toString(), second.id().toString(), third.id().toString());
		assertThat(received.get(0).getPriceAtBooking().getUnits()).isEqualTo(129);
		assertThat(received.get(0).getPriceAtBooking().getNanos()).isEqualTo(500_000_000);
		assertThat(received.get(0).getStatus()).isEqualTo(com.example.grpc.booking.v1.BookingStatus.BOOKING_STATUS_CONFIRMED);
		assertThat(received.get(2).getStatus()).isEqualTo(com.example.grpc.booking.v1.BookingStatus.BOOKING_STATUS_BUMPED);
	}

	@Test
	void missingBookingsAreSimplyAbsentFromTheStream() {
		BookingDto found = booking(BookingStatus.CONFIRMED, new BigDecimal("10"));
		List<UUID> ids = List.of(found.id(), UUID.randomUUID(), UUID.randomUUID());
		when(bookingService.findByIds(ids)).thenReturn(List.of(found));

		List<Booking> received = collect(request(ids));

		assertThat(received).hasSize(1);
	}

	@Test
	void bookingWithoutPriceHasNoPriceInTheMessage() {
		BookingDto failed = booking(BookingStatus.FAILED, null);
		when(bookingService.findByIds(List.of(failed.id()))).thenReturn(List.of(failed));

		List<Booking> received = collect(request(List.of(failed.id())));

		assertThat(received.get(0).hasPriceAtBooking()).isFalse();
	}

	@Test
	void emptyIdListReturnsInvalidArgument() {
		assertThatThrownBy(() -> stub.streamBookings(StreamBookingsRequest.getDefaultInstance()).hasNext())
				.isInstanceOfSatisfying(StatusRuntimeException.class,
						e -> assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT));
		verifyNoInteractions(bookingService);
	}

	@Test
	void malformedIdReturnsInvalidArgument() {
		StreamBookingsRequest request = StreamBookingsRequest.newBuilder().addBookingIds("not-a-uuid").build();

		assertThatThrownBy(() -> stub.streamBookings(request).hasNext())
				.isInstanceOfSatisfying(StatusRuntimeException.class,
						e -> assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT));
	}

	private List<Booking> collect(StreamBookingsRequest request) {
		List<Booking> received = new ArrayList<>();
		stub.streamBookings(request).forEachRemaining(received::add);
		return received;
	}

	private static StreamBookingsRequest request(List<UUID> ids) {
		StreamBookingsRequest.Builder builder = StreamBookingsRequest.newBuilder();
		ids.forEach(id -> builder.addBookingIds(id.toString()));
		return builder.build();
	}

	private static BookingDto booking(BookingStatus status, BigDecimal price) {
		return new BookingDto(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "Ivan Petrenko",
				TicketClass.ECONOMY, status, price, UUID.randomUUID(), null, Instant.now());
	}
}
