package com.example.payment_service.payment.internal;

import com.example.payment_service.payment.PaymentProcessedEvent;
import com.example.payment_service.payment.PaymentProcessedStatus;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxRelayTest {

	@SuppressWarnings("unchecked")
	private final KafkaTemplate<String, PaymentProcessedEvent> kafkaTemplate = mock(KafkaTemplate.class);
	private final OutboxEventRepository repository = mock(OutboxEventRepository.class);
	private final JsonMapper jsonMapper = JsonMapper.builder().build();
	private final OutboxRelay relay = new OutboxRelay(repository, kafkaTemplate, jsonMapper, false);

	private PaymentProcessedEvent result(UUID bookingId) {
		return new PaymentProcessedEvent(UUID.randomUUID(), bookingId, UUID.randomUUID(),
				PaymentProcessedStatus.SUCCESS, null, Instant.now());
	}

	private OutboxEvent event(PaymentProcessedEvent result) {
		return new OutboxEvent(result.eventId(), "Payment", result.bookingId(), "PaymentProcessed",
				jsonMapper.writeValueAsString(result), result.occurredAt());
	}

	@Test
	@SuppressWarnings({"unchecked", "rawtypes"})
	void sendsPendingEventsAsTypedJsonKeyedByBookingIdAndMarksThemProcessed() {
		UUID bookingId = UUID.randomUUID();
		PaymentProcessedEvent result = result(bookingId);
		OutboxEvent event = event(result);
		when(repository.findTop50ByProcessedFalseOrderByCreatedAt()).thenReturn(List.of(event));
		when(kafkaTemplate.send(any(ProducerRecord.class)))
				.thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

		int sent = relay.relayPending();

		assertThat(sent).isEqualTo(1);
		assertThat(event.isProcessed()).isTrue();
		ArgumentCaptor<ProducerRecord<String, PaymentProcessedEvent>> record =
				ArgumentCaptor.forClass((Class) ProducerRecord.class);
		verify(kafkaTemplate).send(record.capture());
		assertThat(record.getValue().topic()).isEqualTo("payment-events");
		assertThat(record.getValue().key()).isEqualTo(bookingId.toString());
		assertThat(record.getValue().value()).isEqualTo(result);
		verify(repository).save(event);
	}

	@Test
	void failedSendLeavesEventPendingForNextCycle() {
		OutboxEvent event = event(result(UUID.randomUUID()));
		when(repository.findTop50ByProcessedFalseOrderByCreatedAt())
				.thenReturn(List.of(event, event(result(UUID.randomUUID()))));
		when(kafkaTemplate.send(any(ProducerRecord.class)))
				.thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker down")));

		int sent = relay.relayPending();

		assertThat(sent).isZero();
		assertThat(event.isProcessed()).isFalse();
		verify(repository, never()).save(any());
	}

	@Test
	void unreadablePayloadIsNotSentAndStaysPending() {
		OutboxEvent broken = new OutboxEvent(UUID.randomUUID(), "Payment", UUID.randomUUID(), "PaymentProcessed",
				"not json", Instant.now());
		when(repository.findTop50ByProcessedFalseOrderByCreatedAt()).thenReturn(List.of(broken));

		int sent = relay.relayPending();

		assertThat(sent).isZero();
		assertThat(broken.isProcessed()).isFalse();
		verify(kafkaTemplate, never()).send(any(ProducerRecord.class));
	}
}
