package com.example.booking_service.booking.internal;

import com.example.booking_service.booking.BookingStatus;
import com.example.booking_service.booking.TicketClass;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BookingTest {

	private final Booking booking = new Booking(UUID.randomUUID(), UUID.randomUUID(), "Ivan Petrenko",
			TicketClass.ECONOMY, Instant.now());

	@Test
	void paymentTransitionsOnlyApplyToReservedBooking() {
		assertThat(booking.confirm(UUID.randomUUID())).isFalse();
		assertThat(booking.failPayment(UUID.randomUUID())).isFalse();
		assertThat(booking.getStatus()).isEqualTo(BookingStatus.PENDING);
		assertThat(booking.getPaymentId()).isNull();

		booking.reserve(new BigDecimal("120.50"), UUID.randomUUID());
		UUID paymentId = UUID.randomUUID();

		assertThat(booking.confirm(paymentId)).isTrue();
		assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
		assertThat(booking.getPaymentId()).isEqualTo(paymentId);
		assertThat(booking.failPayment(UUID.randomUUID())).isFalse();
		assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
	}
}
