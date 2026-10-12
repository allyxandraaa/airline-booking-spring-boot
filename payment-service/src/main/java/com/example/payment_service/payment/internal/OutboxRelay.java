package com.example.payment_service.payment.internal;

import com.example.payment_service.config.KafkaTopicConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

@Component
public class OutboxRelay {

	private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
	private static final long SEND_TIMEOUT_SECONDS = 5;

	private final OutboxEventRepository outboxRepository;
	private final KafkaTemplate<String, String> kafkaTemplate;
	private final boolean enabled;

	OutboxRelay(OutboxEventRepository outboxRepository, KafkaTemplate<String, String> kafkaTemplate,
				@Value("${outbox.relay.enabled:true}") boolean enabled) {
		this.outboxRepository = outboxRepository;
		this.kafkaTemplate = kafkaTemplate;
		this.enabled = enabled;
	}

	@Scheduled(fixedDelay = 500)
	void scheduledRelay() {
		if (enabled) {
			relayPending();
		}
	}

	public int relayPending() {
		int sent = 0;
		for (OutboxEvent event : outboxRepository.findTop50ByProcessedFalseOrderByCreatedAt()) {
			try {
				kafkaTemplate.send(toRecord(event)).get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
			} catch (Exception e) {
				log.warn("Outbox relay failed for event {}: {}", event.getId(), e.toString());
				return sent;
			}
			event.markProcessed();
			outboxRepository.save(event);
			sent++;
		}
		return sent;
	}

	private ProducerRecord<String, String> toRecord(OutboxEvent event) {
		ProducerRecord<String, String> record = new ProducerRecord<>(
				KafkaTopicConfig.PAYMENT_EVENTS_TOPIC, event.getAggregateId().toString(), event.getPayload());
		String correlationId = MDC.get("correlationId");
		if (correlationId != null) {
			record.headers().add("X-Correlation-Id", correlationId.getBytes(StandardCharsets.UTF_8));
		}
		return record;
	}
}
