package com.example.payment_service.payment.internal;

import com.example.payment_service.payment.BookingReservedEvent;
import com.example.payment_service.payment.PaymentService;
import com.example.payment_service.payment.TransientPaymentException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.BackOff;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
class BookingReservedListener {

	private static final Logger log = LoggerFactory.getLogger(BookingReservedListener.class);

	private final PaymentService paymentService;

	BookingReservedListener(PaymentService paymentService) {
		this.paymentService = paymentService;
	}

	@RetryableTopic(attempts = "3",
			backOff = @BackOff(delayString = "${payment.consumer.retry.delay:1000}",
					multiplierString = "${payment.consumer.retry.multiplier:2.0}"),
			include = TransientPaymentException.class,
			dltStrategy = DltStrategy.FAIL_ON_ERROR)
	@KafkaListener(topics = "booking-events", groupId = "payment-service-group")
	public void onBookingReserved(@Payload BookingReservedEvent event,
								  @Header(KafkaHeaders.RECEIVED_KEY) String key,
								  @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
								  @Header(KafkaHeaders.OFFSET) long offset) {
		log.info("Received BookingReservedEvent key={} partition={} offset={} eventId={}",
				key, partition, offset, event.eventId());
		paymentService.processBookingReserved(event);
	}

	@DltHandler
	public void onDeadLetter(@Payload(required = false) BookingReservedEvent event,
							 @Header(KafkaHeaders.RECEIVED_KEY) String key,
							 @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
							 @Header(KafkaHeaders.OFFSET) long offset,
							 @Header(name = KafkaHeaders.EXCEPTION_MESSAGE, required = false) String reason) {
		UUID eventId = event != null ? event.eventId() : null;
		log.error("BookingReservedEvent sent to DLT key={} partition={} offset={} eventId={} reason={}",
				key, partition, offset, eventId, reason);
		try {
			paymentService.recordProcessingFailure(UUID.fromString(key), reason);
		} catch (IllegalArgumentException e) {
			log.error("Cannot determine bookingId from key {}; no FAILED result published", key);
		}
	}
}
