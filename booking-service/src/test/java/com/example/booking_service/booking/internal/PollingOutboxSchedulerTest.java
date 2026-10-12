package com.example.booking_service.booking.internal;

import com.example.booking_service.booking.BookingReservedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.SendResult;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class PollingOutboxSchedulerTest {

	private final OutboxRepository repository = mock(OutboxRepository.class);
	private final BookingEventPublisher publisher = mock(BookingEventPublisher.class);
	private final JsonMapper jsonMapper = JsonMapper.builder().build();
	private final PollingOutboxScheduler scheduler = new PollingOutboxScheduler(repository, publisher, jsonMapper, 1000);

	@Test
	void publishesPendingRecordsAndMarksThemProcessed() {
		BookingReservedEvent event = newEvent("120.50");
		OutboxRecord record = recordOf(event);
		SendResult<String, BookingReservedEvent> sendResult = mock(SendResult.class);
		when(repository.findTop50ByProcessedFalseOrderByCreatedAtAsc()).thenReturn(List.of(record));
		when(publisher.publish(any(BookingReservedEvent.class))).thenReturn(CompletableFuture.completedFuture(sendResult));

		scheduler.processOutbox();

		verify(publisher).publish(event);
		assertThat(record.isProcessed()).isTrue();
	}

	@Test
	void keepsRecordUnprocessedWhenPublishingFails() {
		OutboxRecord record = recordOf(newEvent("120.50"));
		when(repository.findTop50ByProcessedFalseOrderByCreatedAtAsc()).thenReturn(List.of(record));
		when(publisher.publish(any(BookingReservedEvent.class)))
				.thenReturn(CompletableFuture.failedFuture(new IllegalStateException("брокер недоступний")));

		scheduler.processOutbox();

		assertThat(record.isProcessed()).isFalse();
	}

	@Test
	void stopsAtFirstFailureToPreserveOrder() {
		OutboxRecord first = recordOf(newEvent("10.00"));
		OutboxRecord second = recordOf(newEvent("20.00"));
		when(repository.findTop50ByProcessedFalseOrderByCreatedAtAsc()).thenReturn(List.of(first, second));
		when(publisher.publish(any(BookingReservedEvent.class)))
				.thenReturn(CompletableFuture.failedFuture(new IllegalStateException("брокер недоступний")));

		scheduler.processOutbox();

		assertThat(first.isProcessed()).isFalse();
		assertThat(second.isProcessed()).isFalse();
		verify(publisher, times(1)).publish(any(BookingReservedEvent.class));
	}

	private BookingReservedEvent newEvent(String amount) {
		return BookingReservedEvent.now(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal(amount));
	}

	private OutboxRecord recordOf(BookingReservedEvent event) {
		return new OutboxRecord(event.eventId(), "Booking", event.bookingId(), "BookingReserved",
				jsonMapper.writeValueAsString(event), Instant.now());
	}
}
