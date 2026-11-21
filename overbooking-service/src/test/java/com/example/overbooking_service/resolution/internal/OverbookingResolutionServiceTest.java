package com.example.overbooking_service.resolution.internal;

import com.example.overbooking_service.client.BookingClient;
import com.example.overbooking_service.client.FlightClient;
import com.example.overbooking_service.client.dto.ConfirmedBookingDto;
import com.example.overbooking_service.client.dto.FlightCapacityDto;
import com.example.overbooking_service.client.dto.TicketClass;
import com.example.overbooking_service.compensation.CompensationResult;
import com.example.overbooking_service.compensation.CompensationService;
import com.example.overbooking_service.exception.FlightNotFoundException;
import com.example.overbooking_service.exception.ReportNotFoundException;
import com.example.overbooking_service.resolution.BumpOutcome;
import com.example.overbooking_service.resolution.BumpedBookingDto;
import com.example.overbooking_service.resolution.OverbookingReportDto;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OverbookingResolutionServiceTest {

	private static final UUID FLIGHT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
	private static final UUID REPORT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
	private static final Instant START = Instant.parse("2026-11-01T10:00:00Z");
	private static final BigDecimal PRICE = new BigDecimal("100.00");

	private final FlightClient flightClient = mock(FlightClient.class);
	private final BookingClient bookingClient = mock(BookingClient.class);
	private final CompensationService compensationService = mock(CompensationService.class);
	private final OverbookingReportRepository reportRepository = mock(OverbookingReportRepository.class);
	private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
	private final OverbookingResolutionServiceImpl service = new OverbookingResolutionServiceImpl(
			flightClient, bookingClient, compensationService, reportRepository,
			mock(PlatformTransactionManager.class), registry);

	@BeforeEach
	void setUpRepository() {
		when(reportRepository.save(any(OverbookingReport.class))).thenAnswer(invocation -> {
			OverbookingReport report = invocation.getArgument(0);
			ReflectionTestUtils.setField(report, "id", REPORT_ID);
			return report;
		});
	}

	@Test
	void flightWithinCapacityProducesEmptyReport() {
		when(flightClient.getCapacity(FLIGHT_ID)).thenReturn(new FlightCapacityDto(2, 1));
		when(bookingClient.getConfirmedBookings(FLIGHT_ID)).thenReturn(bookings(TicketClass.ECONOMY, 2));

		OverbookingReportDto report = service.resolve(FLIGHT_ID);

		assertThat(report.id()).isEqualTo(REPORT_ID);
		assertThat(report.bumpedCount()).isZero();
		assertThat(report.bumpedBookings()).isEmpty();
		assertThat(report.totalCompensation()).isEqualByComparingTo("0");
		verify(bookingClient, never()).bump(any());
		verifyNoInteractions(compensationService);
	}

	@Test
	void latestPurchasesAreBumpedAndCompensated() {
		List<ConfirmedBookingDto> economy = bookings(TicketClass.ECONOMY, 4);
		List<ConfirmedBookingDto> business = bookings(TicketClass.BUSINESS, 1);
		when(flightClient.getCapacity(FLIGHT_ID)).thenReturn(new FlightCapacityDto(2, 1));
		when(bookingClient.getConfirmedBookings(FLIGHT_ID)).thenReturn(concat(economy, business));
		UUID latest = economy.get(3).id();
		UUID previous = economy.get(2).id();
		when(compensationService.compensate(List.of(latest, previous))).thenReturn(List.of(
				new CompensationResult(latest, PRICE, "COMPLETED"),
				new CompensationResult(previous, PRICE, "COMPLETED")));

		OverbookingReportDto report = service.resolve(FLIGHT_ID);

		verify(bookingClient).bump(latest);
		verify(bookingClient).bump(previous);
		assertThat(report.seatsEconomy()).isEqualTo(2);
		assertThat(report.seatsBusiness()).isEqualTo(1);
		assertThat(report.bumpedCount()).isEqualTo(2);
		assertThat(report.totalCompensation()).isEqualByComparingTo("200.00");
		assertThat(report.bumpedBookings()).extracting(BumpedBookingDto::bookingId).containsExactly(latest, previous);
		assertThat(report.bumpedBookings()).extracting(BumpedBookingDto::outcome)
				.containsOnly(BumpOutcome.COMPENSATED);
		assertThat(registry.get("overbooking.bumped.total").tag("outcome", "COMPENSATED").counter().count())
				.isEqualTo(2.0);
	}

	@Test
	void deferredRefundIsReportedAndNotCountedAsPaid() {
		List<ConfirmedBookingDto> economy = bookings(TicketClass.ECONOMY, 3);
		when(flightClient.getCapacity(FLIGHT_ID)).thenReturn(new FlightCapacityDto(1, 1));
		when(bookingClient.getConfirmedBookings(FLIGHT_ID)).thenReturn(economy);
		UUID latest = economy.get(2).id();
		UUID previous = economy.get(1).id();
		when(compensationService.compensate(List.of(latest, previous))).thenReturn(List.of(
				new CompensationResult(latest, PRICE, "COMPLETED"),
				new CompensationResult(previous, PRICE, "PENDING")));

		OverbookingReportDto report = service.resolve(FLIGHT_ID);

		assertThat(report.bumpedBookings()).extracting(BumpedBookingDto::outcome)
				.containsExactly(BumpOutcome.COMPENSATED, BumpOutcome.REFUND_PENDING);
		assertThat(report.totalCompensation()).isEqualByComparingTo("100.00");
	}

	@Test
	void bookingThatCouldNotBeBumpedIsReportedAndNotRefunded() {
		List<ConfirmedBookingDto> economy = bookings(TicketClass.ECONOMY, 3);
		when(flightClient.getCapacity(FLIGHT_ID)).thenReturn(new FlightCapacityDto(1, 1));
		when(bookingClient.getConfirmedBookings(FLIGHT_ID)).thenReturn(economy);
		UUID latest = economy.get(2).id();
		UUID previous = economy.get(1).id();
		doThrow(new StatusRuntimeException(Status.UNAVAILABLE)).when(bookingClient).bump(latest);
		when(compensationService.compensate(List.of(previous)))
				.thenReturn(List.of(new CompensationResult(previous, PRICE, "COMPLETED")));

		OverbookingReportDto report = service.resolve(FLIGHT_ID);

		verify(compensationService).compensate(List.of(previous));
		assertThat(report.bumpedCount()).isEqualTo(1);
		assertThat(report.bumpedBookings()).extracting(BumpedBookingDto::outcome)
				.containsExactly(BumpOutcome.BUMP_FAILED, BumpOutcome.COMPENSATED);
		assertThat(report.totalCompensation()).isEqualByComparingTo("100.00");
	}

	@Test
	void compensationFailureLeavesRefundsPendingButKeepsTheReport() {
		List<ConfirmedBookingDto> economy = bookings(TicketClass.ECONOMY, 2);
		when(flightClient.getCapacity(FLIGHT_ID)).thenReturn(new FlightCapacityDto(1, 1));
		when(bookingClient.getConfirmedBookings(FLIGHT_ID)).thenReturn(economy);
		when(compensationService.compensate(anyList())).thenThrow(new IllegalStateException("payment is down"));

		OverbookingReportDto report = service.resolve(FLIGHT_ID);

		assertThat(report.bumpedBookings()).extracting(BumpedBookingDto::outcome)
				.containsExactly(BumpOutcome.REFUND_PENDING);
		assertThat(report.totalCompensation()).isEqualByComparingTo("0");
		verify(reportRepository).save(any(OverbookingReport.class));
	}

	@Test
	void unknownFlightStopsBeforeAnyBookingIsTouched() {
		when(flightClient.getCapacity(FLIGHT_ID)).thenThrow(new FlightNotFoundException(FLIGHT_ID));

		assertThatThrownBy(() -> service.resolve(FLIGHT_ID)).isInstanceOf(FlightNotFoundException.class);

		verifyNoInteractions(bookingClient, compensationService, reportRepository);
	}

	@Test
	void storedReportIsReturnedById() {
		OverbookingReport stored = new OverbookingReport(FLIGHT_ID, 2, 1, PRICE, START, List.of(
				new BumpedBooking(UUID.randomUUID(), TicketClass.ECONOMY, PRICE, BumpOutcome.COMPENSATED)));
		ReflectionTestUtils.setField(stored, "id", REPORT_ID);
		when(reportRepository.findById(REPORT_ID)).thenReturn(Optional.of(stored));

		OverbookingReportDto report = service.findReport(REPORT_ID);

		assertThat(report.id()).isEqualTo(REPORT_ID);
		assertThat(report.flightId()).isEqualTo(FLIGHT_ID);
		assertThat(report.bumpedCount()).isEqualTo(1);
	}

	@Test
	void unknownReportIsNotFound() {
		when(reportRepository.findById(REPORT_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.findReport(REPORT_ID)).isInstanceOf(ReportNotFoundException.class);
	}

	private static List<ConfirmedBookingDto> bookings(TicketClass ticketClass, int count) {
		return IntStream.range(0, count)
				.mapToObj(hour -> new ConfirmedBookingDto(UUID.randomUUID(), ticketClass, PRICE,
						START.plusSeconds(3600L * hour)))
				.toList();
	}

	private static List<ConfirmedBookingDto> concat(List<ConfirmedBookingDto> first, List<ConfirmedBookingDto> second) {
		return Stream.concat(first.stream(), second.stream()).toList();
	}
}
