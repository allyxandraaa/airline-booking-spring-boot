package com.example.overbooking_service.resolution.internal;

import com.example.overbooking_service.client.BookingClient;
import com.example.overbooking_service.client.FlightClient;
import com.example.overbooking_service.client.dto.ConfirmedBookingDto;
import com.example.overbooking_service.client.dto.FlightCapacityDto;
import com.example.overbooking_service.client.dto.TicketClass;
import com.example.overbooking_service.compensation.CompensationResult;
import com.example.overbooking_service.compensation.CompensationService;
import com.example.overbooking_service.exception.ReportNotFoundException;
import com.example.overbooking_service.resolution.BumpOutcome;
import com.example.overbooking_service.resolution.BumpedBookingDto;
import com.example.overbooking_service.resolution.OverbookingReportDto;
import com.example.overbooking_service.resolution.OverbookingResolutionService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@DataJpaTest(properties = "DB_PASSWORD=unused")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({OverbookingResolutionServiceImpl.class, OverbookingPostgresTest.MetricsConfig.class})
class OverbookingPostgresTest {

	private static final BigDecimal PRICE = new BigDecimal("100.00");
	private static final Instant START = Instant.parse("2026-11-01T10:00:00Z");

	@ServiceConnection
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

	static {
		POSTGRES.start();
	}

	@TestConfiguration
	static class MetricsConfig {

		@Bean
		MeterRegistry meterRegistry() {
			return new SimpleMeterRegistry();
		}
	}

	@MockitoBean
	FlightClient flightClient;

	@MockitoBean
	BookingClient bookingClient;

	@MockitoBean
	CompensationService compensationService;

	@Autowired
	OverbookingResolutionService service;

	@Autowired
	OverbookingReportRepository reportRepository;

	@AfterEach
	void cleanDatabase() {
		reportRepository.deleteAll();
	}

	@Test
	void resolutionStoresReportWithBumpedBookingsAndReadsItBack() {
		UUID flightId = UUID.randomUUID();
		ConfirmedBookingDto early = confirmed(0);
		ConfirmedBookingDto late = confirmed(5);
		when(flightClient.getCapacity(flightId)).thenReturn(new FlightCapacityDto(1, 1));
		when(bookingClient.getConfirmedBookings(flightId)).thenReturn(List.of(early, late));
		when(compensationService.compensate(anyList()))
				.thenReturn(List.of(new CompensationResult(late.id(), PRICE, "COMPLETED")));

		OverbookingReportDto created = service.resolve(flightId);
		OverbookingReportDto loaded = service.findReport(created.id());

		assertThat(reportRepository.count()).isEqualTo(1);
		assertThat(loaded.flightId()).isEqualTo(flightId);
		assertThat(loaded.seatsEconomy()).isEqualTo(1);
		assertThat(loaded.bumpedCount()).isEqualTo(1);
		assertThat(loaded.totalCompensation()).isEqualByComparingTo("100.00");
		assertThat(loaded.bumpedBookings()).singleElement().satisfies(booking -> {
			assertThat(booking.bookingId()).isEqualTo(late.id());
			assertThat(booking.outcome()).isEqualTo(BumpOutcome.COMPENSATED);
			assertThat(booking.amount()).isEqualByComparingTo("100.00");
		});
	}

	@Test
	void flightWithinCapacityStoresReportWithoutBumpedBookings() {
		UUID flightId = UUID.randomUUID();
		when(flightClient.getCapacity(flightId)).thenReturn(new FlightCapacityDto(2, 1));
		when(bookingClient.getConfirmedBookings(flightId)).thenReturn(List.of(confirmed(0), confirmed(1)));

		OverbookingReportDto created = service.resolve(flightId);

		OverbookingReportDto loaded = service.findReport(created.id());
		assertThat(loaded.bumpedCount()).isZero();
		assertThat(loaded.bumpedBookings()).isEmpty();
	}

	@Test
	void refundDeferredByPaymentIsStoredAsPending() {
		UUID flightId = UUID.randomUUID();
		ConfirmedBookingDto early = confirmed(0);
		ConfirmedBookingDto late = confirmed(5);
		when(flightClient.getCapacity(flightId)).thenReturn(new FlightCapacityDto(1, 1));
		when(bookingClient.getConfirmedBookings(flightId)).thenReturn(List.of(early, late));
		when(compensationService.compensate(anyList()))
				.thenReturn(List.of(new CompensationResult(late.id(), PRICE, "PENDING")));

		OverbookingReportDto loaded = service.findReport(service.resolve(flightId).id());

		assertThat(loaded.bumpedBookings()).extracting(BumpedBookingDto::outcome).containsExactly(BumpOutcome.REFUND_PENDING);
		assertThat(loaded.totalCompensation()).isEqualByComparingTo("0");
	}

	@Test
	void unknownReportIsNotFound() {
		UUID unknownId = UUID.randomUUID();

		assertThatThrownBy(() -> service.findReport(unknownId)).isInstanceOf(ReportNotFoundException.class);
	}

	private static ConfirmedBookingDto confirmed(int hour) {
		return new ConfirmedBookingDto(UUID.randomUUID(), TicketClass.ECONOMY, PRICE, START.plusSeconds(3600L * hour));
	}
}
