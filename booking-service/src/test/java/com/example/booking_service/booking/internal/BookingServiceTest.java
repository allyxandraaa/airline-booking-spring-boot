package com.example.booking_service.booking.internal;

import com.example.booking_service.booking.BookingDto;
import com.example.booking_service.booking.BookingStatus;
import com.example.booking_service.booking.CreateBookingRequest;
import com.example.booking_service.booking.TicketClass;
import com.example.booking_service.exception.BookingNotFoundException;
import com.example.booking_service.exception.BookingProcessingException;
import com.example.booking_service.integration.FlightGateway;
import com.example.booking_service.integration.PassengerGateway;
import com.example.booking_service.integration.SeatUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BookingServiceTest {

	private final BookingRepository repository = mock(BookingRepository.class);
	private final OutboxRepository outboxRepository = mock(OutboxRepository.class);
	private final FlightGateway flightGateway = mock(FlightGateway.class);
	private final PassengerGateway passengerGateway = mock(PassengerGateway.class);
	private final JsonMapper jsonMapper = JsonMapper.builder().build();
	private final BookingServiceImpl service = new BookingServiceImpl(
			repository, outboxRepository, flightGateway, passengerGateway,
			mock(PlatformTransactionManager.class), jsonMapper);

	private final Map<UUID, Booking> store = new HashMap<>();
	private final CreateBookingRequest request = new CreateBookingRequest(
			UUID.randomUUID(), UUID.randomUUID(), "Ivan Petrenko", TicketClass.ECONOMY);

	@BeforeEach
	void setUpRepository() {
		when(repository.save(any(Booking.class))).thenAnswer(invocation -> {
			Booking booking = invocation.getArgument(0);
			if (booking.getId() == null) {
				ReflectionTestUtils.setField(booking, "id", UUID.randomUUID());
			}
			store.put(booking.getId(), booking);
			return booking;
		});
		when(repository.findById(any(UUID.class)))
				.thenAnswer(invocation -> Optional.ofNullable(store.get(invocation.<UUID>getArgument(0))));
	}

	@Test
	void loadsAllBookingsWithSingleRepositoryCall() {
		List<UUID> ids = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
		List<Booking> bookings = ids.stream()
				.map(id -> new Booking(UUID.randomUUID(), UUID.randomUUID(), "Ivan Petrenko", TicketClass.ECONOMY,
						BookingStatus.CONFIRMED, new BigDecimal("100.00"), Instant.now()))
				.toList();
		when(repository.findAllById(ids)).thenReturn(bookings);

		List<BookingDto> result = service.findByIds(ids);

		assertThat(result).hasSize(3);
		assertThat(result).allSatisfy(dto -> assertThat(dto.status()).isEqualTo(BookingStatus.CONFIRMED));
		verify(repository, times(1)).findAllById(ids);
		verify(repository, never()).findById(any());
	}

	@Test
	void createReservesSeatAndStoresPaymentRequestInOutbox() {
		UUID reservationId = UUID.randomUUID();
		UUID paymentMethodId = UUID.randomUUID();
		when(flightGateway.getPrice(request.flightId(), TicketClass.ECONOMY)).thenReturn(new BigDecimal("120.50"));
		when(flightGateway.reserveSeat(request.flightId(), TicketClass.ECONOMY)).thenReturn(reservationId);
		when(passengerGateway.getPaymentMethodId(request.passengerId())).thenReturn(paymentMethodId);

		BookingDto dto = service.create(request);

		assertThat(dto.status()).isEqualTo(BookingStatus.SEAT_RESERVED);
		assertThat(dto.priceAtBooking()).isEqualByComparingTo("120.50");
		assertThat(dto.reservationId()).isEqualTo(reservationId);
		ArgumentCaptor<OutboxRecord> captor = ArgumentCaptor.forClass(OutboxRecord.class);
		verify(outboxRepository).save(captor.capture());
		OutboxRecord outbox = captor.getValue();
		assertThat(outbox.getId()).isNotNull();
		assertThat(outbox.getAggregateId()).isEqualTo(dto.id());
		assertThat(outbox.getEventType()).isEqualTo("BookingReserved");
		assertThat(outbox.isProcessed()).isFalse();
		assertThat(outbox.getPayload())
				.contains(outbox.getId().toString())
				.contains(dto.id().toString())
				.contains(paymentMethodId.toString());
		verify(flightGateway, never()).releaseSeat(any());
	}

	@Test
	void createMarksBookingFailedWhenNoSeatsLeft() {
		when(flightGateway.getPrice(any(), any())).thenReturn(new BigDecimal("120.50"));
		when(flightGateway.reserveSeat(any(), any())).thenThrow(new SeatUnavailableException("місць немає"));

		assertThatThrownBy(() -> service.create(request)).isInstanceOf(SeatUnavailableException.class);

		assertThat(store.values()).singleElement()
				.satisfies(booking -> assertThat(booking.getStatus()).isEqualTo(BookingStatus.FAILED));
		verifyNoInteractions(outboxRepository);
		verify(flightGateway, never()).releaseSeat(any());
	}

	@Test
	void createReleasesSeatAndFailsBookingWhenSomethingBreaksAfterReservation() {
		UUID reservationId = UUID.randomUUID();
		when(flightGateway.getPrice(any(), any())).thenReturn(new BigDecimal("120.50"));
		when(flightGateway.reserveSeat(any(), any())).thenReturn(reservationId);
		when(passengerGateway.getPaymentMethodId(any())).thenThrow(new IllegalStateException("сервіс пасажирів недоступний"));

		assertThatThrownBy(() -> service.create(request)).isInstanceOf(BookingProcessingException.class);

		verify(flightGateway).releaseSeat(reservationId);
		assertThat(store.values()).singleElement()
				.satisfies(booking -> assertThat(booking.getStatus()).isEqualTo(BookingStatus.FAILED));
		verifyNoInteractions(outboxRepository);
	}

	@Test
	void createStillFailsBookingWhenSeatReleaseFails() {
		UUID reservationId = UUID.randomUUID();
		when(flightGateway.getPrice(any(), any())).thenReturn(new BigDecimal("120.50"));
		when(flightGateway.reserveSeat(any(), any())).thenReturn(reservationId);
		when(passengerGateway.getPaymentMethodId(any())).thenThrow(new IllegalStateException("сервіс пасажирів недоступний"));
		doThrow(new IllegalStateException("сервіс рейсів недоступний")).when(flightGateway).releaseSeat(reservationId);

		assertThatThrownBy(() -> service.create(request)).isInstanceOf(BookingProcessingException.class);

		assertThat(store.values()).singleElement()
				.satisfies(booking -> assertThat(booking.getStatus()).isEqualTo(BookingStatus.FAILED));
	}

	@Test
	void findByIdReturnsBooking() {
		Booking booking = new Booking(UUID.randomUUID(), UUID.randomUUID(), "Ivan Petrenko", TicketClass.BUSINESS,
				Instant.now());
		repository.save(booking);

		BookingDto dto = service.findById(booking.getId());

		assertThat(dto.id()).isEqualTo(booking.getId());
		assertThat(dto.status()).isEqualTo(BookingStatus.PENDING);
		assertThat(dto.ticketClass()).isEqualTo(TicketClass.BUSINESS);
	}

	@Test
	void findByIdThrowsWhenBookingMissing() {
		assertThatThrownBy(() -> service.findById(UUID.randomUUID())).isInstanceOf(BookingNotFoundException.class);
	}
}
