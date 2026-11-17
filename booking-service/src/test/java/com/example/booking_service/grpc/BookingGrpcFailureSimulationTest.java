package com.example.booking_service.grpc;

import com.example.booking_service.booking.BookingDto;
import com.example.booking_service.booking.BookingService;
import com.example.booking_service.booking.BookingStatus;
import com.example.booking_service.booking.PaymentResultService;
import com.example.booking_service.booking.TicketClass;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = "booking.grpc.failures-before-success=2")
@ActiveProfiles("test")
@AutoConfigureTestGrpcTransport
@ImportGrpcClients(types = BookingServiceGrpc.BookingServiceBlockingStub.class)
class BookingGrpcFailureSimulationTest {

	@Autowired
	BookingServiceGrpc.BookingServiceBlockingStub stub;

	@MockitoBean
	BookingService bookingService;

	@MockitoBean
	PaymentResultService paymentResultService;

	@Test
	void firstTwoCallsFailWithUnavailableAndThirdSucceeds() {
		UUID id = UUID.randomUUID();
		BookingDto booking = new BookingDto(id, UUID.randomUUID(), UUID.randomUUID(), "Ivan Petrenko",
				TicketClass.ECONOMY, BookingStatus.CONFIRMED, new BigDecimal("100"), UUID.randomUUID(), null,
				Instant.now());
		when(bookingService.findByIds(anyList())).thenReturn(List.of(booking));
		StreamBookingsRequest request = StreamBookingsRequest.newBuilder().addBookingIds(id.toString()).build();

		for (int attempt = 1; attempt <= 2; attempt++) {
			assertThatThrownBy(() -> stub.streamBookings(request).hasNext())
					.isInstanceOfSatisfying(StatusRuntimeException.class,
							e -> assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.UNAVAILABLE));
		}
		assertThat(stub.streamBookings(request).next().getId()).isEqualTo(id.toString());
		verify(bookingService, times(1)).findByIds(anyList());
	}
}
