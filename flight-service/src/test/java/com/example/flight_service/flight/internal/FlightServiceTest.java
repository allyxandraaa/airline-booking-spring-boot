package com.example.flight_service.flight.internal;

import com.example.flight_service.exception.FlightNotFoundException;
import com.example.flight_service.exception.ReservationNotFoundException;
import com.example.flight_service.exception.SeatUnavailableException;
import com.example.flight_service.flight.CreateFlightRequest;
import com.example.flight_service.flight.FlightDto;
import com.example.flight_service.flight.ReservationDto;
import com.example.flight_service.flight.TicketClass;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FlightServiceTest {

	private final FlightRepository flightRepository = mock(FlightRepository.class);
	private final InventoryRepository inventoryRepository = mock(InventoryRepository.class);
	private final ReservationRepository reservationRepository = mock(ReservationRepository.class);
	private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
	private final FlightServiceImpl service = new FlightServiceImpl(
			flightRepository, inventoryRepository, reservationRepository, mock(PlatformTransactionManager.class), registry);

	private final UUID flightId = UUID.randomUUID();

	@BeforeEach
	void setUpRepositories() {
		when(inventoryRepository.save(any(Inventory.class))).then(returnsFirstArg());
		when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> {
			Reservation reservation = invocation.getArgument(0);
			ReflectionTestUtils.setField(reservation, "id", UUID.randomUUID());
			return reservation;
		});
	}

	@Test
	void createStoresFlightWithInventoryAndReturnsDto() {
		Instant departure = Instant.now().plus(Duration.ofDays(40));
		when(flightRepository.save(any(Flight.class))).thenAnswer(invocation -> {
			Flight flight = invocation.getArgument(0);
			ReflectionTestUtils.setField(flight, "id", flightId);
			return flight;
		});

		FlightDto dto = service.create(new CreateFlightRequest(
				"PS101", "KBP", "WAW", departure, 150, 12,
				new BigDecimal("120.00"), new BigDecimal("250.00"), 10));

		assertThat(dto.id()).isEqualTo(flightId);
		assertThat(dto.flightNumber()).isEqualTo("PS101");
		assertThat(dto.totalSeatsEconomy()).isEqualTo(150);
		assertThat(dto.basePriceBusiness()).isEqualByComparingTo("250.00");
		assertThat(dto.overbookingLimitPercent()).isEqualTo(10);
		verify(inventoryRepository).save(any(Inventory.class));
	}

	@ParameterizedTest
	@CsvSource({"40,108.00", "20,120.00", "10,144.00", "5,168.00", "1,192.00"})
	void priceOfEmptyFlightDependsOnDaysLeft(int daysLeft, String expected) {
		givenFlight(daysLeft, 100, 0, 0);

		assertThat(service.calculatePrice(flightId, TicketClass.ECONOMY)).isEqualByComparingTo(expected);
	}

	@Test
	void priceGrowsWithOccupancy() {
		givenFlight(20, 100, 50, 0);

		assertThat(service.calculatePrice(flightId, TicketClass.ECONOMY)).isEqualByComparingTo("168.00");
	}

	@Test
	void priceOccupancyIsCappedWhenOverbooked() {
		givenFlight(20, 100, 105, 0);

		assertThat(service.calculatePrice(flightId, TicketClass.ECONOMY)).isEqualByComparingTo("216.00");
	}

	@Test
	void businessPriceUsesBusinessBaseAndOccupancy() {
		givenFlight(20, 100, 0, 6);

		assertThat(service.calculatePrice(flightId, TicketClass.BUSINESS)).isEqualByComparingTo("350.00");
	}

	@Test
	void priceIsRecordedInSummaryOfItsTicketClass() {
		givenFlight(20, 100, 0, 0);

		service.calculatePrice(flightId, TicketClass.ECONOMY);
		service.calculatePrice(flightId, TicketClass.ECONOMY);

		DistributionSummary economy = registry.get("flights.price.economy").summary();
		assertThat(economy.count()).isEqualTo(2);
		assertThat(economy.totalAmount()).isEqualTo(240.0);
		assertThat(registry.get("flights.price.business").summary().count()).isZero();
	}

	@Test
	void priceForUnknownFlightThrows() {
		when(flightRepository.findById(flightId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.calculatePrice(flightId, TicketClass.ECONOMY))
				.isInstanceOf(FlightNotFoundException.class);
	}

	@Test
	void reserveSeatIncrementsSoldAndReturnsReservation() {
		Inventory inventory = givenFlight(20, 100, 0, 0);

		ReservationDto reservation = service.reserveSeat(flightId, TicketClass.ECONOMY);

		assertThat(reservation.reservationId()).isNotNull();
		assertThat(inventory.sold(TicketClass.ECONOMY)).isEqualTo(1);
		assertThat(inventory.sold(TicketClass.BUSINESS)).isZero();
	}

	@Test
	void reserveSeatAllowsOverbookingUpToLimit() {
		Inventory inventory = givenFlight(20, 100, 109, 0);

		service.reserveSeat(flightId, TicketClass.ECONOMY);

		assertThat(inventory.sold(TicketClass.ECONOMY)).isEqualTo(110);
	}

	@Test
	void reserveSeatBeyondOverbookingLimitThrowsAndStoresNothing() {
		Inventory inventory = givenFlight(20, 100, 110, 0);

		assertThatThrownBy(() -> service.reserveSeat(flightId, TicketClass.ECONOMY))
				.isInstanceOf(SeatUnavailableException.class);

		assertThat(inventory.sold(TicketClass.ECONOMY)).isEqualTo(110);
		verify(reservationRepository, never()).save(any());
	}

	@Test
	void reserveSeatForUnknownFlightThrows() {
		when(inventoryRepository.findByFlightId(flightId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.reserveSeat(flightId, TicketClass.ECONOMY))
				.isInstanceOf(FlightNotFoundException.class);
	}

	@Test
	void releaseSeatDecrementsSoldOnce() {
		Inventory inventory = givenFlight(20, 100, 5, 0);
		UUID reservationId = UUID.randomUUID();
		when(reservationRepository.findWithLockById(reservationId))
				.thenReturn(Optional.of(new Reservation(flightId, TicketClass.ECONOMY)));

		service.releaseSeat(reservationId);
		service.releaseSeat(reservationId);

		assertThat(inventory.sold(TicketClass.ECONOMY)).isEqualTo(4);
	}

	@Test
	void releaseSeatForUnknownReservationThrows() {
		UUID reservationId = UUID.randomUUID();
		when(reservationRepository.findWithLockById(reservationId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.releaseSeat(reservationId))
				.isInstanceOf(ReservationNotFoundException.class);
	}

	private Inventory givenFlight(int daysLeft, int economySeats, int soldEconomy, int soldBusiness) {
		Flight flight = new Flight("PS101", "KBP", "WAW", Instant.now().plus(Duration.ofDays(daysLeft)),
				new BigDecimal("120.00"), new BigDecimal("250.00"));
		Inventory inventory = new Inventory(flightId, economySeats, 12, 10);
		ReflectionTestUtils.setField(inventory, "seatsSoldEconomy", soldEconomy);
		ReflectionTestUtils.setField(inventory, "seatsSoldBusiness", soldBusiness);
		when(flightRepository.findById(flightId)).thenReturn(Optional.of(flight));
		when(inventoryRepository.findById(flightId)).thenReturn(Optional.of(inventory));
		when(inventoryRepository.findByFlightId(flightId)).thenReturn(Optional.of(inventory));
		return inventory;
	}
}
