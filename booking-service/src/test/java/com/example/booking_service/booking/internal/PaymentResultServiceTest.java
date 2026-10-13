package com.example.booking_service.booking.internal;

import com.example.booking_service.booking.BookingStatus;
import com.example.booking_service.booking.PaymentProcessedEvent;
import com.example.booking_service.booking.PaymentProcessedStatus;
import com.example.booking_service.booking.TicketClass;
import com.example.booking_service.exception.TransientBookingException;
import com.example.booking_service.integration.FlightGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentResultServiceTest {

	private final BookingRepository bookingRepository = mock(BookingRepository.class);
	private final ProcessedEventRepository processedEventRepository = mock(ProcessedEventRepository.class);
	private final FlightGateway flightGateway = mock(FlightGateway.class);
	private final PaymentResultServiceImpl service = new PaymentResultServiceImpl(
			bookingRepository, processedEventRepository, flightGateway, mock(PlatformTransactionManager.class));

	private final UUID reservationId = UUID.randomUUID();
	private final UUID paymentId = UUID.randomUUID();
	private Booking booking;

	@BeforeEach
	void setUp() {
		booking = new Booking(UUID.randomUUID(), UUID.randomUUID(), "Ivan Petrenko", TicketClass.ECONOMY, Instant.now());
		ReflectionTestUtils.setField(booking, "id", UUID.randomUUID());
		booking.reserve(new BigDecimal("120.50"), reservationId);
		when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));
	}

	private PaymentProcessedEvent event(PaymentProcessedStatus status, UUID paymentId) {
		return PaymentProcessedEvent.now(booking.getId(), paymentId, status, null);
	}

	@Test
	void successConfirmsBookingAndStoresPaymentId() {
		service.applyPaymentResult(event(PaymentProcessedStatus.SUCCESS, paymentId));

		assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
		assertThat(booking.getPaymentId()).isEqualTo(paymentId);
		verify(processedEventRepository).saveAndFlush(any(ProcessedEvent.class));
		verify(flightGateway, never()).releaseSeat(any());
	}

	@Test
	void declinedReleasesSeatOnceAndFailsBooking() {
		service.applyPaymentResult(event(PaymentProcessedStatus.DECLINED, paymentId));

		verify(flightGateway, times(1)).releaseSeat(reservationId);
		assertThat(booking.getStatus()).isEqualTo(BookingStatus.FAILED);
		assertThat(booking.getPaymentId()).isEqualTo(paymentId);
		verify(processedEventRepository).saveAndFlush(any(ProcessedEvent.class));
	}

	@Test
	void failedWithoutPaymentReleasesSeatAndFailsBooking() {
		service.applyPaymentResult(event(PaymentProcessedStatus.FAILED, null));

		verify(flightGateway, times(1)).releaseSeat(reservationId);
		assertThat(booking.getStatus()).isEqualTo(BookingStatus.FAILED);
		assertThat(booking.getPaymentId()).isNull();
	}

	@Test
	void duplicateEventIsSkippedWithoutSideEffects() {
		PaymentProcessedEvent event = event(PaymentProcessedStatus.DECLINED, paymentId);
		when(processedEventRepository.existsById(event.eventId())).thenReturn(true);

		service.applyPaymentResult(event);

		assertThat(booking.getStatus()).isEqualTo(BookingStatus.SEAT_RESERVED);
		verify(flightGateway, never()).releaseSeat(any());
		verify(bookingRepository, never()).save(any());
	}

	@Test
	void concurrentDuplicateIsTreatedAsProcessed() {
		doThrow(new DataIntegrityViolationException("duplicate key"))
				.when(processedEventRepository).saveAndFlush(any(ProcessedEvent.class));

		service.applyPaymentResult(event(PaymentProcessedStatus.SUCCESS, paymentId));
	}

	@Test
	void resultForAlreadyFinishedBookingIsIgnored() {
		booking.confirm(UUID.randomUUID());

		service.applyPaymentResult(event(PaymentProcessedStatus.DECLINED, paymentId));

		assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
		verify(flightGateway, never()).releaseSeat(any());
		verify(processedEventRepository, never()).saveAndFlush(any());
	}

	@Test
	void unknownBookingIsIgnored() {
		service.applyPaymentResult(PaymentProcessedEvent.now(UUID.randomUUID(), paymentId, PaymentProcessedStatus.SUCCESS, null));

		verify(bookingRepository, never()).save(any());
		verify(processedEventRepository, never()).saveAndFlush(any());
	}

	@Test
	void seatReleaseFailureIsTransientAndLeavesBookingReserved() {
		doThrow(new IllegalStateException("flight service down")).when(flightGateway).releaseSeat(reservationId);

		assertThatThrownBy(() -> service.applyPaymentResult(event(PaymentProcessedStatus.DECLINED, paymentId)))
				.isInstanceOf(TransientBookingException.class);

		assertThat(booking.getStatus()).isEqualTo(BookingStatus.SEAT_RESERVED);
		verify(processedEventRepository, never()).saveAndFlush(any());
	}
}
