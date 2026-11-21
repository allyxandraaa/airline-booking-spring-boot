package com.example.flight_service.flight.internal;

import com.example.flight_service.exception.SeatUnavailableException;
import com.example.flight_service.flight.CreateFlightRequest;
import com.example.flight_service.flight.FlightCapacityDto;
import com.example.flight_service.flight.ReservationDto;
import com.example.flight_service.flight.TicketClass;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = "DB_PASSWORD=unused")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class FlightPostgresTest {

	@ServiceConnection
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

	static {
		POSTGRES.start();
	}

	@Autowired
	FlightRepository flightRepository;

	@Autowired
	InventoryRepository inventoryRepository;

	@Autowired
	ReservationRepository reservationRepository;

	@Autowired
	PlatformTransactionManager transactionManager;

	private FlightServiceImpl service;

	@BeforeEach
	void setUp() {
		service = new FlightServiceImpl(flightRepository, inventoryRepository, reservationRepository,
				transactionManager, new SimpleMeterRegistry());
	}

	@AfterEach
	void cleanDatabase() {
		reservationRepository.deleteAll();
		inventoryRepository.deleteAll();
		flightRepository.deleteAll();
	}

	@Test
	void salesStopAtPhysicalSeatsPlusOverbookingLimit() {
		UUID flightId = flight(2, 1, 50);

		service.reserveSeat(flightId, TicketClass.ECONOMY);
		service.reserveSeat(flightId, TicketClass.ECONOMY);
		service.reserveSeat(flightId, TicketClass.ECONOMY);

		assertThatThrownBy(() -> service.reserveSeat(flightId, TicketClass.ECONOMY))
				.isInstanceOf(SeatUnavailableException.class);
		service.reserveSeat(flightId, TicketClass.BUSINESS);
	}

	@Test
	void releasedSeatCanBeSoldAgain() {
		UUID flightId = flight(1, 1, 0);
		ReservationDto reservation = service.reserveSeat(flightId, TicketClass.ECONOMY);
		assertThatThrownBy(() -> service.reserveSeat(flightId, TicketClass.ECONOMY))
				.isInstanceOf(SeatUnavailableException.class);

		service.releaseSeat(reservation.reservationId());

		assertThat(service.reserveSeat(flightId, TicketClass.ECONOMY).reservationId()).isNotNull();
	}

	@Test
	void releasingTheSameReservationTwiceFreesOnlyOneSeat() {
		UUID flightId = flight(1, 1, 100);
		ReservationDto first = service.reserveSeat(flightId, TicketClass.ECONOMY);
		service.reserveSeat(flightId, TicketClass.ECONOMY);

		service.releaseSeat(first.reservationId());
		service.releaseSeat(first.reservationId());

		service.reserveSeat(flightId, TicketClass.ECONOMY);
		assertThatThrownBy(() -> service.reserveSeat(flightId, TicketClass.ECONOMY))
				.isInstanceOf(SeatUnavailableException.class);
	}

	@Test
	void priceGrowsWithOccupancy() {
		UUID flightId = flight(2, 1, 0);
		BigDecimal empty = service.calculatePrice(flightId, TicketClass.ECONOMY);

		service.reserveSeat(flightId, TicketClass.ECONOMY);

		assertThat(service.calculatePrice(flightId, TicketClass.ECONOMY)).isGreaterThan(empty);
	}

	@Test
	void capacityReportsPhysicalSeatsWithoutOverbooking() {
		UUID flightId = flight(150, 12, 10);

		FlightCapacityDto capacity = service.getCapacity(flightId);

		assertThat(capacity.seatsEconomy()).isEqualTo(150);
		assertThat(capacity.seatsBusiness()).isEqualTo(12);
	}

	private UUID flight(int economySeats, int businessSeats, int overbookingPercent) {
		return service.create(new CreateFlightRequest("PS" + UUID.randomUUID().toString().substring(0, 4), "KBP", "WAW",
				Instant.now().plus(Duration.ofDays(40)), economySeats, businessSeats,
				new BigDecimal("120.00"), new BigDecimal("250.00"), overbookingPercent)).id();
	}
}
