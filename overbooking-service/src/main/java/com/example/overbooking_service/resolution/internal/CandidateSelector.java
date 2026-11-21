package com.example.overbooking_service.resolution.internal;

import com.example.overbooking_service.client.dto.ConfirmedBookingDto;
import com.example.overbooking_service.client.dto.TicketClass;

import java.util.Comparator;
import java.util.List;

final class CandidateSelector {

	private static final Comparator<ConfirmedBookingDto> LATEST_PURCHASE_FIRST =
			Comparator.comparing(ConfirmedBookingDto::purchaseTime)
					.thenComparing(ConfirmedBookingDto::id)
					.reversed();

	private CandidateSelector() {
	}

	static List<ConfirmedBookingDto> select(List<ConfirmedBookingDto> confirmed, TicketClass ticketClass, int seats) {
		List<ConfirmedBookingDto> sameClass = confirmed.stream()
				.filter(booking -> booking.ticketClass() == ticketClass)
				.sorted(LATEST_PURCHASE_FIRST)
				.toList();
		int excess = sameClass.size() - seats;
		return excess > 0 ? sameClass.subList(0, excess) : List.of();
	}
}
