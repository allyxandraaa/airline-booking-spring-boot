package com.example.overbooking_service.compensation.internal;

import com.example.overbooking_service.client.BookingClient;
import com.example.overbooking_service.client.dto.BatchRequest;
import com.example.overbooking_service.client.dto.BookingDto;
import com.example.overbooking_service.compensation.CompensationResult;
import com.example.overbooking_service.compensation.CompensationService;
import com.example.overbooking_service.compensation.CompensationSummary;
import com.example.overbooking_service.exception.BookingsNotFoundException;
import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
class CompensationServiceImpl implements CompensationService {

	private final BookingClient bookingClient;
	private final RefundGateway refundGateway;
	private final LongTaskTimer compensationTimer;

	CompensationServiceImpl(BookingClient bookingClient, RefundGateway refundGateway, MeterRegistry registry) {
		this.bookingClient = bookingClient;
		this.refundGateway = refundGateway;
		this.compensationTimer = LongTaskTimer.builder("overbooking.compensation")
				.description("Активні пакетні компенсації бронювань")
				.register(registry);
	}

	@Override
	public List<CompensationResult> compensate(List<UUID> bookingIds) {
		LongTaskTimer.Sample sample = compensationTimer.start();
		try {
			return refundGateway.refundAll(loadOrders(bookingIds));
		} finally {
			sample.stop();
		}
	}

	@Override
	public CompensationSummary summarize(List<UUID> bookingIds) {
		return refundGateway.refundBatch(loadOrders(bookingIds));
	}

	private List<RefundOrder> loadOrders(List<UUID> bookingIds) {
		List<UUID> ids = bookingIds.stream().distinct().toList();
		Map<UUID, BookingDto> bookings = bookingClient.getBookingsByIds(new BatchRequest(ids)).stream()
				.collect(Collectors.toMap(BookingDto::id, Function.identity(), (first, second) -> first));

		List<UUID> missing = ids.stream().filter(id -> !bookings.containsKey(id)).toList();
		if (!missing.isEmpty()) {
			throw new BookingsNotFoundException(missing);
		}

		return ids.stream()
				.map(id -> new RefundOrder(id, bookings.get(id).priceAtBooking(), idempotencyKey(id)))
				.toList();
	}

	private String idempotencyKey(UUID bookingId) {
		return UUID.nameUUIDFromBytes(("refund:" + bookingId).getBytes(StandardCharsets.UTF_8)).toString();
	}
}
