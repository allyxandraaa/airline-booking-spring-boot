package com.example.payment_service.payment.internal;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

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
	private final KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
	private final OutboxEventRepository repository = mock(OutboxEventRepository.class);
	private final OutboxRelay relay = new OutboxRelay(repository, kafkaTemplate, false);

	private OutboxEvent event(UUID bookingId) {
		return new OutboxEvent(UUID.randomUUID(), "Payment", bookingId, "PaymentProcessed", "{}", Instant.now());
	}

	@Test
	void sendsPendingEventsKeyedByBookingIdAndMarksThemProcessed() {
		UUID bookingId = UUID.randomUUID();
		OutboxEvent event = event(bookingId);
		when(repository.findTop50ByProcessedFalseOrderByCreatedAt()).thenReturn(List.of(event));
		when(kafkaTemplate.send(any(ProducerRecord.class)))
				.thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

		int sent = relay.relayPending();

		assertThat(sent).isEqualTo(1);
		assertThat(event.isProcessed()).isTrue();
		ArgumentCaptor<ProducerRecord<String, String>> record = ArgumentCaptor.forClass(ProducerRecord.class);
		verify(kafkaTemplate).send(record.capture());
		assertThat(record.getValue().topic()).isEqualTo("payment-events");
		assertThat(record.getValue().key()).isEqualTo(bookingId.toString());
		verify(repository).save(event);
	}

	@Test
	void failedSendLeavesEventPendingForNextCycle() {
		OutboxEvent event = event(UUID.randomUUID());
		when(repository.findTop50ByProcessedFalseOrderByCreatedAt()).thenReturn(List.of(event, event(UUID.randomUUID())));
		when(kafkaTemplate.send(any(ProducerRecord.class)))
				.thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker down")));

		int sent = relay.relayPending();

		assertThat(sent).isZero();
		assertThat(event.isProcessed()).isFalse();
		verify(repository, never()).save(any());
	}
}
