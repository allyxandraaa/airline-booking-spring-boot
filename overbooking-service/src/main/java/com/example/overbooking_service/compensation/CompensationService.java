package com.example.overbooking_service.compensation;

import com.example.overbooking_service.client.BookingClient;
import com.example.overbooking_service.client.dto.BatchRequest;
import com.example.overbooking_service.client.dto.BookingDto;
import com.example.overbooking_service.exception.BookingsNotFoundException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CompensationService {

	private final BookingClient bookingClient;
	private final RefundGateway refundGateway;

	public CompensationService(BookingClient bookingClient, RefundGateway refundGateway) {
		this.bookingClient = bookingClient;
		this.refundGateway = refundGateway;
	}

	public List<CompensationResult> compensate(List<UUID> bookingIds) {
		List<UUID> ids = bookingIds.stream().distinct().toList();
		Map<UUID, BookingDto> bookings = bookingClient.getBookingsByIds(new BatchRequest(ids)).stream()
				.collect(Collectors.toMap(BookingDto::id, Function.identity(), (first, second) -> first));

		List<UUID> missing = ids.stream().filter(id -> !bookings.containsKey(id)).toList();
		if (!missing.isEmpty()) {
			throw new BookingsNotFoundException(missing);
		}

		return ids.stream()
				.map(id -> refundGateway.refund(id, bookings.get(id).priceAtBooking(), idempotencyKey(id)))
				.toList();
	}

	private String idempotencyKey(UUID bookingId) {
		return UUID.nameUUIDFromBytes(("refund:" + bookingId).getBytes(StandardCharsets.UTF_8)).toString();
	}
}
