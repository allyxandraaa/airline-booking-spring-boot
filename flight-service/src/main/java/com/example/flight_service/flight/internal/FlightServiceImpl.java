package com.example.flight_service.flight.internal;

import com.example.flight_service.exception.FlightNotFoundException;
import com.example.flight_service.exception.ReservationNotFoundException;
import com.example.flight_service.exception.SeatUnavailableException;
import com.example.flight_service.flight.CreateFlightRequest;
import com.example.flight_service.flight.FlightDto;
import com.example.flight_service.flight.FlightService;
import com.example.flight_service.flight.ReservationDto;
import com.example.flight_service.flight.TicketClass;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
class FlightServiceImpl implements FlightService {

	private static final BigDecimal OCCUPANCY_WEIGHT = new BigDecimal("0.8");

	private final FlightRepository flightRepository;
	private final InventoryRepository inventoryRepository;
	private final ReservationRepository reservationRepository;
	private final TransactionTemplate transactionTemplate;
	private final DistributionSummary economyPriceSummary;
	private final DistributionSummary businessPriceSummary;

	FlightServiceImpl(FlightRepository flightRepository,
					  InventoryRepository inventoryRepository,
					  ReservationRepository reservationRepository,
					  PlatformTransactionManager transactionManager,
					  MeterRegistry registry) {
		this.flightRepository = flightRepository;
		this.inventoryRepository = inventoryRepository;
		this.reservationRepository = reservationRepository;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
		this.economyPriceSummary = DistributionSummary.builder("flights.price.economy")
				.description("Розподіл розрахованих цін економ-класу")
				.serviceLevelObjectives(100, 125, 150, 200, 250, 300, 350)
				.register(registry);
		this.businessPriceSummary = DistributionSummary.builder("flights.price.business")
				.description("Розподіл розрахованих цін бізнес-класу")
				.serviceLevelObjectives(200, 250, 300, 400, 500, 600, 750)
				.register(registry);
	}

	@Override
	public FlightDto create(CreateFlightRequest request) {
		return transactionTemplate.execute(status -> {
			Flight flight = flightRepository.save(new Flight(
					request.flightNumber(), request.origin(), request.destination(), request.departureTime(),
					request.basePriceEconomy(), request.basePriceBusiness()));
			Inventory inventory = inventoryRepository.save(new Inventory(
					flight.getId(), request.totalSeatsEconomy(), request.totalSeatsBusiness(),
					request.overbookingLimitPercent()));
			return toDto(flight, inventory);
		});
	}

	@Override
	@Transactional(readOnly = true)
	public BigDecimal calculatePrice(UUID flightId, TicketClass ticketClass) {
		Flight flight = flightRepository.findById(flightId)
				.orElseThrow(() -> new FlightNotFoundException(flightId));
		Inventory inventory = inventoryRepository.findById(flightId)
				.orElseThrow(() -> new FlightNotFoundException(flightId));
		BigDecimal price = flight.basePrice(ticketClass)
				.multiply(BigDecimal.ONE.add(OCCUPANCY_WEIGHT.multiply(inventory.occupancy(ticketClass))))
				.multiply(timeFactor(flight.getDepartureTime()))
				.setScale(2, RoundingMode.HALF_UP);
		(ticketClass == TicketClass.ECONOMY ? economyPriceSummary : businessPriceSummary).record(price.doubleValue());
		return price;
	}

	@Override
	public ReservationDto reserveSeat(UUID flightId, TicketClass ticketClass) {
		return transactionTemplate.execute(status -> {
			Inventory inventory = inventoryRepository.findByFlightId(flightId)
					.orElseThrow(() -> new FlightNotFoundException(flightId));
			if (!inventory.reserve(ticketClass)) {
				throw new SeatUnavailableException(flightId, ticketClass);
			}
			Reservation reservation = reservationRepository.save(new Reservation(flightId, ticketClass));
			return new ReservationDto(reservation.getId());
		});
	}

	@Override
	public void releaseSeat(UUID reservationId) {
		transactionTemplate.executeWithoutResult(status -> {
			Reservation reservation = reservationRepository.findWithLockById(reservationId)
					.orElseThrow(() -> new ReservationNotFoundException(reservationId));
			if (reservation.release()) {
				inventoryRepository.findByFlightId(reservation.getFlightId())
						.orElseThrow(() -> new FlightNotFoundException(reservation.getFlightId()))
						.release(reservation.getTicketClass());
			}
		});
	}

	private BigDecimal timeFactor(Instant departureTime) {
		long daysLeft = Duration.between(Instant.now(), departureTime).toDays();
		if (daysLeft >= 30) {
			return new BigDecimal("0.90");
		}
		if (daysLeft >= 14) {
			return BigDecimal.ONE;
		}
		if (daysLeft >= 7) {
			return new BigDecimal("1.20");
		}
		if (daysLeft >= 3) {
			return new BigDecimal("1.40");
		}
		return new BigDecimal("1.60");
	}

	private FlightDto toDto(Flight flight, Inventory inventory) {
		return new FlightDto(
				flight.getId(),
				flight.getFlightNumber(),
				flight.getOrigin(),
				flight.getDestination(),
				flight.getDepartureTime(),
				inventory.getTotalSeatsEconomy(),
				inventory.getTotalSeatsBusiness(),
				flight.getBasePriceEconomy(),
				flight.getBasePriceBusiness(),
				inventory.getOverbookingLimitPercent());
	}
}
