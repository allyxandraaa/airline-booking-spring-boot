package com.example.payment_service.payment.internal;

import com.example.payment_service.config.KafkaTopicConfig;
import com.example.payment_service.payment.PaymentProcessedEvent;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

@Component
public class OutboxRelay {

	private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
	private static final long SEND_TIMEOUT_SECONDS = 5;

	private final OutboxEventRepository outboxRepository;
	private final KafkaTemplate<String, PaymentProcessedEvent> kafkaTemplate;
	private final JsonMapper jsonMapper;
	private final OutboxTracing tracing;
	private final boolean enabled;

	OutboxRelay(OutboxEventRepository outboxRepository, KafkaTemplate<String, PaymentProcessedEvent> kafkaTemplate,
				JsonMapper jsonMapper, OutboxTracing tracing, @Value("${outbox.relay.enabled:true}") boolean enabled) {
		this.outboxRepository = outboxRepository;
		this.kafkaTemplate = kafkaTemplate;
		this.jsonMapper = jsonMapper;
		this.tracing = tracing;
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
				tracing.call(event.getTraceparent(),
						() -> kafkaTemplate.send(toRecord(event)).get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS));
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

	private ProducerRecord<String, PaymentProcessedEvent> toRecord(OutboxEvent event) {
		PaymentProcessedEvent payload = jsonMapper.readValue(event.getPayload(), PaymentProcessedEvent.class);
		ProducerRecord<String, PaymentProcessedEvent> record = new ProducerRecord<>(
				KafkaTopicConfig.PAYMENT_EVENTS_TOPIC, event.getAggregateId().toString(), payload);
		String correlationId = MDC.get("correlationId");
		if (correlationId != null) {
			record.headers().add("X-Correlation-Id", correlationId.getBytes(StandardCharsets.UTF_8));
		}
		return record;
	}
}
