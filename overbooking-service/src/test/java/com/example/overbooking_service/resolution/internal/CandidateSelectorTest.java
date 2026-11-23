package com.example.overbooking_service.resolution.internal;

import com.example.overbooking_service.client.dto.ConfirmedBookingDto;
import com.example.overbooking_service.client.dto.TicketClass;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class CandidateSelectorTest {

	private static final Instant START = Instant.parse("2026-11-01T10:00:00Z");

	@Test
	void flightWithinCapacityHasNoCandidates() {
		List<ConfirmedBookingDto> bookings = bookings(TicketClass.ECONOMY, 3);

		assertThat(CandidateSelector.select(bookings, TicketClass.ECONOMY, 3)).isEmpty();
		assertThat(CandidateSelector.select(bookings, TicketClass.ECONOMY, 5)).isEmpty();
	}

	@Test
	void excessIsTakenFromTheLatestPurchases() {
		List<ConfirmedBookingDto> bookings = bookings(TicketClass.ECONOMY, 5);

		List<ConfirmedBookingDto> candidates = CandidateSelector.select(bookings, TicketClass.ECONOMY, 3);

		assertThat(candidates).extracting(ConfirmedBookingDto::id)
				.containsExactly(bookings.get(4).id(), bookings.get(3).id());
	}

	@Test
	void ticketClassesAreCheckedIndependently() {
		List<ConfirmedBookingDto> economy = bookings(TicketClass.ECONOMY, 1);
		List<ConfirmedBookingDto> business = bookings(TicketClass.BUSINESS, 2);
		List<ConfirmedBookingDto> all = new ArrayList<>(economy);
		all.addAll(business);

		assertThat(CandidateSelector.select(all, TicketClass.ECONOMY, 1)).isEmpty();
		assertThat(CandidateSelector.select(all, TicketClass.BUSINESS, 1)).extracting(ConfirmedBookingDto::id)
				.containsExactly(business.get(1).id());
	}

	@Test
	void selectionDoesNotDependOnInputOrder() {
		List<ConfirmedBookingDto> bookings = bookings(TicketClass.ECONOMY, 6);
		List<ConfirmedBookingDto> shuffled = new ArrayList<>(bookings);
		Collections.reverse(shuffled);

		assertThat(CandidateSelector.select(shuffled, TicketClass.ECONOMY, 4))
				.isEqualTo(CandidateSelector.select(bookings, TicketClass.ECONOMY, 4));
	}

	@Test
	void bookingsWithEqualPurchaseTimeAreOrderedByIdentifier() {
		ConfirmedBookingDto first = new ConfirmedBookingDto(UUID.fromString("00000000-0000-0000-0000-000000000001"),
				TicketClass.ECONOMY, new BigDecimal("100.00"), START);
		ConfirmedBookingDto second = new ConfirmedBookingDto(UUID.fromString("00000000-0000-0000-0000-000000000002"),
				TicketClass.ECONOMY, new BigDecimal("100.00"), START);

		assertThat(CandidateSelector.select(List.of(first, second), TicketClass.ECONOMY, 1)).containsExactly(second);
		assertThat(CandidateSelector.select(List.of(second, first), TicketClass.ECONOMY, 1)).containsExactly(second);
	}

	private static List<ConfirmedBookingDto> bookings(TicketClass ticketClass, int count) {
		return IntStream.range(0, count)
				.mapToObj(hour -> new ConfirmedBookingDto(UUID.randomUUID(), ticketClass, new BigDecimal("100.00"),
						START.plusSeconds(3600L * hour)))
				.toList();
	}
}
