package com.example.overbooking_service.compensation.internal;

import com.example.overbooking_service.client.BookingClient;
import com.example.overbooking_service.client.dto.BookingDto;
import com.example.overbooking_service.compensation.CompensationResult;
import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CompensationServiceTest {

	private final BookingClient bookingClient = mock(BookingClient.class);
	private final RefundGateway refundGateway = mock(RefundGateway.class);
	private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
	private final CompensationServiceImpl service = new CompensationServiceImpl(bookingClient, refundGateway, registry);

	private LongTaskTimer timer() {
		return registry.get("overbooking.compensation").longTaskTimer();
	}

	@Test
	void compensationIsTimedWhileRunningAndReleasedAfterwards() {
		UUID id = UUID.randomUUID();
		when(bookingClient.getBookingsByIds(any())).thenAnswer(invocation -> {
			assertThat(timer().activeTasks()).isEqualTo(1);
			return List.of(new BookingDto(id, new BigDecimal("120.50"), "BUMPED"));
		});
		when(refundGateway.refund(any(), any(), anyString()))
				.thenReturn(new CompensationResult(id, new BigDecimal("120.50"), "COMPLETED"));

		service.compensate(List.of(id));

		assertThat(timer().activeTasks()).isZero();
	}

	@Test
	void failedCompensationDoesNotLeaveActiveTask() {
		when(bookingClient.getBookingsByIds(any())).thenThrow(new IllegalStateException("booking service down"));

		assertThatThrownBy(() -> service.compensate(List.of(UUID.randomUUID()))).isInstanceOf(IllegalStateException.class);

		assertThat(timer().activeTasks()).isZero();
	}
}
