package com.example.overbooking_service.resolution.internal;

import com.example.overbooking_service.client.BookingClient;
import com.example.overbooking_service.client.FlightClient;
import com.example.overbooking_service.client.dto.ConfirmedBookingDto;
import com.example.overbooking_service.client.dto.FlightCapacityDto;
import com.example.overbooking_service.client.dto.TicketClass;
import com.example.overbooking_service.compensation.CompensationService;
import com.example.overbooking_service.exception.ReportNotFoundException;
import com.example.overbooking_service.resolution.BumpOutcome;
import com.example.overbooking_service.resolution.BumpedBookingDto;
import com.example.overbooking_service.resolution.OverbookingReportDto;
import com.example.overbooking_service.resolution.OverbookingResolutionService;
import io.grpc.StatusRuntimeException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
class OverbookingResolutionServiceImpl implements OverbookingResolutionService {

	private static final Logger log = LoggerFactory.getLogger(OverbookingResolutionServiceImpl.class);

	private final FlightClient flightClient;
	private final BookingClient bookingClient;
	private final CompensationService compensationService;
	private final OverbookingReportRepository reportRepository;
	private final TransactionTemplate transactionTemplate;
	private final MeterRegistry registry;

	OverbookingResolutionServiceImpl(FlightClient flightClient,
									 BookingClient bookingClient,
									 CompensationService compensationService,
									 OverbookingReportRepository reportRepository,
									 PlatformTransactionManager transactionManager,
									 MeterRegistry registry) {
		this.flightClient = flightClient;
		this.bookingClient = bookingClient;
		this.compensationService = compensationService;
		this.reportRepository = reportRepository;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
		this.registry = registry;
	}

	@Override
	public OverbookingReportDto resolve(UUID flightId) {
		FlightCapacityDto capacity = flightClient.getCapacity(flightId);
		List<ConfirmedBookingDto> confirmed = bookingClient.getConfirmedBookings(flightId);
		List<ConfirmedBookingDto> candidates = Arrays.stream(TicketClass.values())
				.flatMap(ticketClass -> CandidateSelector.select(confirmed, ticketClass, capacity.seats(ticketClass)).stream())
				.toList();

		Map<UUID, BumpOutcome> outcomes = new HashMap<>();
		compensate(bump(candidates, outcomes), outcomes);

		List<BumpedBooking> bumped = candidates.stream()
				.map(candidate -> new BumpedBooking(candidate.id(), candidate.ticketClass(),
						candidate.priceAtBooking(), outcomes.getOrDefault(candidate.id(), BumpOutcome.REFUND_PENDING)))
				.toList();
		bumped.forEach(booking -> counter(booking.getOutcome()).increment());

		return transactionTemplate.execute(status -> toDto(reportRepository.save(new OverbookingReport(
				flightId, capacity.seatsEconomy(), capacity.seatsBusiness(), totalCompensation(bumped),
				Instant.now(), bumped))));
	}

	@Override
	@Transactional(readOnly = true)
	public OverbookingReportDto findReport(UUID reportId) {
		return reportRepository.findById(reportId)
				.map(this::toDto)
				.orElseThrow(() -> new ReportNotFoundException(reportId));
	}

	private List<UUID> bump(List<ConfirmedBookingDto> candidates, Map<UUID, BumpOutcome> outcomes) {
		List<UUID> bumpedIds = new ArrayList<>();
		for (ConfirmedBookingDto candidate : candidates) {
			try {
				bookingClient.bump(candidate.id());
				bumpedIds.add(candidate.id());
			} catch (StatusRuntimeException e) {
				log.warn("Не вдалося зняти бронювання {} з рейсу: {}", candidate.id(), e.getStatus());
				outcomes.put(candidate.id(), BumpOutcome.BUMP_FAILED);
			}
		}
		return bumpedIds;
	}

	private void compensate(List<UUID> bumpedIds, Map<UUID, BumpOutcome> outcomes) {
		if (bumpedIds.isEmpty()) {
			return;
		}
		try {
			compensationService.compensate(bumpedIds).forEach(result -> outcomes.put(result.bookingId(),
					result.completed() ? BumpOutcome.COMPENSATED : BumpOutcome.REFUND_PENDING));
		} catch (RuntimeException e) {
			log.error("Компенсацію знятих бронювань {} не виконано, повернення лишається в очікуванні", bumpedIds, e);
			bumpedIds.forEach(id -> outcomes.put(id, BumpOutcome.REFUND_PENDING));
		}
	}

	private Counter counter(BumpOutcome outcome) {
		return Counter.builder("overbooking.bumped.total")
				.description("Зняті з рейсу бронювання за результатом")
				.tag("outcome", outcome.name())
				.register(registry);
	}

	private static BigDecimal totalCompensation(List<BumpedBooking> bumped) {
		return bumped.stream()
				.filter(booking -> booking.getOutcome() == BumpOutcome.COMPENSATED)
				.map(BumpedBooking::getAmount)
				.reduce(BigDecimal.ZERO, BigDecimal::add);
	}

	private OverbookingReportDto toDto(OverbookingReport report) {
		List<BumpedBookingDto> items = report.getBumpedBookings().stream()
				.map(booking -> new BumpedBookingDto(booking.getBookingId(), booking.getTicketClass(),
						booking.getAmount(), booking.getOutcome()))
				.toList();
		int bumpedCount = (int) items.stream().filter(item -> item.outcome() != BumpOutcome.BUMP_FAILED).count();
		return new OverbookingReportDto(report.getId(), report.getFlightId(), report.getSeatsEconomy(),
				report.getSeatsBusiness(), bumpedCount, report.getTotalCompensation(), report.getCreatedAt(), items);
	}
}
