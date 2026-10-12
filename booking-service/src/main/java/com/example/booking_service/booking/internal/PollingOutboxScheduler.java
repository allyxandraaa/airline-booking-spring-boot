package com.example.booking_service.booking.internal;

import com.example.booking_service.booking.BookingReservedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(name = "outbox.scheduler.enabled", havingValue = "true", matchIfMissing = true)
class PollingOutboxScheduler {

	private static final Logger log = LoggerFactory.getLogger(PollingOutboxScheduler.class);

	private final OutboxRepository outboxRepository;
	private final BookingEventPublisher publisher;
	private final JsonMapper jsonMapper;
	private final long sendTimeoutMs;

	PollingOutboxScheduler(OutboxRepository outboxRepository,
						   BookingEventPublisher publisher,
						   JsonMapper jsonMapper,
						   @Value("${outbox.scheduler.send-timeout-ms:5000}") long sendTimeoutMs) {
		this.outboxRepository = outboxRepository;
		this.publisher = publisher;
		this.jsonMapper = jsonMapper;
		this.sendTimeoutMs = sendTimeoutMs;
	}

	@Scheduled(fixedDelayString = "${outbox.scheduler.delay-ms:500}")
	@Transactional
	public void processOutbox() {
		List<OutboxRecord> pending = outboxRepository.findTop50ByProcessedFalseOrderByCreatedAtAsc();
		for (OutboxRecord record : pending) {
			try {
				BookingReservedEvent event = jsonMapper.readValue(record.getPayload(), BookingReservedEvent.class);
				publisher.publish(event).get(sendTimeoutMs, TimeUnit.MILLISECONDS);
				record.markProcessed();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return;
			} catch (Exception e) {
				log.warn("Подію outbox {} не доставлено, повторна спроба в наступному циклі", record.getId(), e);
				return;
			}
		}
	}
}
