package com.example.booking_service.booking.internal;

import com.example.booking_service.booking.BookingDto;
import com.example.booking_service.booking.BookingStatus;
import com.example.booking_service.booking.CreateBookingRequest;
import com.example.booking_service.booking.TicketClass;
import com.example.booking_service.integration.FlightGateway;
import com.example.booking_service.integration.PassengerGateway;
import com.example.booking_service.integration.PassengerNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DataJpaTest(properties = "DB_PASSWORD=unused")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class BookingPostgresTest {

	@ServiceConnection
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

	static {
		POSTGRES.start();
	}

	@Autowired
	BookingRepository bookingRepository;

	@Autowired
	OutboxRepository outboxRepository;

	@Autowired
	ProcessedEventRepository processedEventRepository;

	@Autowired
	PlatformTransactionManager transactionManager;

	private final FlightGateway flightGateway = mock(FlightGateway.class);
	private final PassengerGateway passengerGateway = mock(PassengerGateway.class);
	private BookingServiceImpl service;

	@BeforeEach
	void setUp() {
		service = new BookingServiceImpl(bookingRepository, outboxRepository, flightGateway, passengerGateway,
				transactionManager, JsonMapper.builder().build(), mock(OutboxTracing.class));
	}

	@AfterEach
	void cleanDatabase() {
		outboxRepository.deleteAll();
		bookingRepository.deleteAll();
		processedEventRepository.deleteAll();
	}

	@Test
	void createStoresReservedBookingAndOutboxEventTogether() {
		CreateBookingRequest request = request();
		UUID reservationId = UUID.randomUUID();
		when(flightGateway.getPrice(request.flightId(), TicketClass.ECONOMY)).thenReturn(new BigDecimal("120.50"));
		when(flightGateway.reserveSeat(request.flightId(), TicketClass.ECONOMY)).thenReturn(reservationId);
		when(passengerGateway.getPaymentMethodId(request.passengerId())).thenReturn(UUID.randomUUID());

		BookingDto dto = service.create(request);

		Booking stored = bookingRepository.findById(dto.id()).orElseThrow();
		assertThat(stored.getStatus()).isEqualTo(BookingStatus.SEAT_RESERVED);
		assertThat(stored.getPriceAtBooking()).isEqualByComparingTo("120.50");
		assertThat(stored.getReservationId()).isEqualTo(reservationId);
		List<OutboxRecord> pending = pendingOutboxEvents();
		assertThat(pending).hasSize(1);
		assertThat(pending.get(0).getPayload()).contains(dto.id().toString());
	}

	@Test
	void failedPassengerLookupFailsBookingReleasesSeatAndLeavesNoOutboxEvent() {
		CreateBookingRequest request = request();
		UUID reservationId = UUID.randomUUID();
		when(flightGateway.getPrice(request.flightId(), TicketClass.ECONOMY)).thenReturn(new BigDecimal("99.00"));
		when(flightGateway.reserveSeat(request.flightId(), TicketClass.ECONOMY)).thenReturn(reservationId);
		when(passengerGateway.getPaymentMethodId(request.passengerId()))
				.thenThrow(new PassengerNotFoundException(request.passengerId()));

		assertThatThrownBy(() -> service.create(request)).isInstanceOf(PassengerNotFoundException.class);

		assertThat(bookingRepository.findAll()).singleElement()
				.satisfies(booking -> assertThat(booking.getStatus()).isEqualTo(BookingStatus.FAILED));
		assertThat(outboxRepository.count()).isZero();
		verify(flightGateway).releaseSeat(reservationId);
	}

	@Test
	void rollbackDiscardsBookingAndOutboxEventTogether() {
		TransactionTemplate template = new TransactionTemplate(transactionManager);
		Booking booking = new Booking(UUID.randomUUID(), UUID.randomUUID(), "Ivan Petrenko", TicketClass.BUSINESS, Instant.now());

		assertThatThrownBy(() -> template.executeWithoutResult(status -> saveBookingAndOutboxThenFail(booking)))
				.isInstanceOf(IllegalStateException.class);

		assertThat(bookingRepository.count()).isZero();
		assertThat(outboxRepository.count()).isZero();
	}

	@Test
	void pendingOutboxEventsAreReadOldestFirstAndProcessedOnesAreSkipped() {
		Instant start = Instant.parse("2026-11-01T10:00:00Z");
		OutboxRecord oldest = new OutboxRecord(UUID.randomUUID(), "Booking", UUID.randomUUID(), "BookingReserved", "{}", start);
		OutboxRecord middle = new OutboxRecord(UUID.randomUUID(), "Booking", UUID.randomUUID(), "BookingReserved", "{}", start.plusSeconds(10));
		OutboxRecord newest = new OutboxRecord(UUID.randomUUID(), "Booking", UUID.randomUUID(), "BookingReserved", "{}", start.plusSeconds(20));
		middle.markProcessed();
		outboxRepository.saveAll(List.of(newest, middle, oldest));

		assertThat(pendingOutboxEvents())
				.extracting(OutboxRecord::getId).containsExactly(oldest.getId(), newest.getId());
		assertThat(outboxRepository.countByProcessedFalse()).isEqualTo(2);
	}

	@Test
	void confirmedBookingsAreFoundByFlightAndStatus() {
		UUID flightId = UUID.randomUUID();
		Booking confirmed = bookingRepository.save(booking(flightId, BookingStatus.CONFIRMED));
		bookingRepository.save(booking(flightId, BookingStatus.SEAT_RESERVED));
		bookingRepository.save(booking(UUID.randomUUID(), BookingStatus.CONFIRMED));

		assertThat(bookingRepository.findByFlightIdAndStatus(flightId, BookingStatus.CONFIRMED))
				.extracting(Booking::getId).containsExactly(confirmed.getId());
	}

	@Test
	void sameEventKeyCannotBeStoredTwice() {
		UUID eventId = UUID.randomUUID();
		processedEventRepository.saveAndFlush(new ProcessedEvent(eventId, Instant.now()));
		ProcessedEvent duplicate = new ProcessedEvent(eventId, Instant.now());

		assertThatThrownBy(() -> processedEventRepository.saveAndFlush(duplicate))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThat(processedEventRepository.count()).isEqualTo(1);
	}

	private void saveBookingAndOutboxThenFail(Booking booking) {
		bookingRepository.save(booking);
		outboxRepository.save(new OutboxRecord(UUID.randomUUID(), "Booking", booking.getId(), "BookingReserved", "{}", Instant.now()));
		throw new IllegalStateException("failure before commit");
	}

	private List<OutboxRecord> pendingOutboxEvents() {
		return new TransactionTemplate(transactionManager)
				.execute(status -> outboxRepository.findTop50ByProcessedFalseOrderByCreatedAtAsc());
	}

	private static CreateBookingRequest request() {
		return new CreateBookingRequest(UUID.randomUUID(), UUID.randomUUID(), "Ivan Petrenko", TicketClass.ECONOMY);
	}

	private static Booking booking(UUID flightId, BookingStatus status) {
		return new Booking(flightId, UUID.randomUUID(), "Ivan Petrenko", TicketClass.ECONOMY, status,
				new BigDecimal("100.00"), Instant.now());
	}
}
