package com.example.booking_service.booking.internal;

import com.example.booking_service.booking.BookingReservedEvent;
import com.example.booking_service.config.KafkaTopicConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.MDC;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

@Service
class BookingEventPublisher {

	private final KafkaTemplate<String, BookingReservedEvent> kafkaTemplate;

	BookingEventPublisher(KafkaTemplate<String, BookingReservedEvent> kafkaTemplate) {
		this.kafkaTemplate = kafkaTemplate;
	}

	CompletableFuture<SendResult<String, BookingReservedEvent>> publish(BookingReservedEvent event) {
		ProducerRecord<String, BookingReservedEvent> record =
				new ProducerRecord<>(KafkaTopicConfig.BOOKING_EVENTS_TOPIC, event.bookingId().toString(), event);
		String correlationId = MDC.get("correlationId");
		if (correlationId != null) {
			record.headers().add("X-Correlation-Id", correlationId.getBytes(StandardCharsets.UTF_8));
		}
		return kafkaTemplate.send(record);
	}
}
