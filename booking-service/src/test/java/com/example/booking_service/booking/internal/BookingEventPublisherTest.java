package com.example.booking_service.booking.internal;

import com.example.booking_service.booking.BookingReservedEvent;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@SuppressWarnings({"unchecked", "rawtypes"})
class BookingEventPublisherTest {

	private final KafkaTemplate<String, BookingReservedEvent> kafkaTemplate = mock(KafkaTemplate.class);
	private final BookingEventPublisher publisher = new BookingEventPublisher(kafkaTemplate);
	private final BookingReservedEvent event =
			BookingReservedEvent.now(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("120.50"));

	@AfterEach
	void clearMdc() {
		MDC.clear();
	}

	@Test
	void sendsEventToBookingEventsTopicWithBookingIdAsKey() {
		publisher.publish(event);

		ProducerRecord<String, BookingReservedEvent> record = capturedRecord();
		assertThat(record.topic()).isEqualTo("booking-events");
		assertThat(record.key()).isEqualTo(event.bookingId().toString());
		assertThat(record.value()).isEqualTo(event);
		assertThat(record.headers().lastHeader("X-Correlation-Id")).isNull();
	}

	@Test
	void addsCorrelationIdHeaderWhenPresentInMdc() {
		MDC.put("correlationId", "abc-123");

		publisher.publish(event);

		assertThat(capturedRecord().headers().lastHeader("X-Correlation-Id").value())
				.isEqualTo("abc-123".getBytes(StandardCharsets.UTF_8));
	}

	private ProducerRecord<String, BookingReservedEvent> capturedRecord() {
		ArgumentCaptor<ProducerRecord<String, BookingReservedEvent>> captor =
				ArgumentCaptor.forClass((Class) ProducerRecord.class);
		verify(kafkaTemplate).send(captor.capture());
		return captor.getValue();
	}
}
