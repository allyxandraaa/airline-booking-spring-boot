package com.example.booking_service.booking.internal;

import com.example.booking_service.booking.PaymentProcessedEvent;
import com.example.booking_service.booking.PaymentResultService;
import com.example.booking_service.exception.TransientBookingException;
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

@Component
class PaymentProcessedListener {

	private static final Logger log = LoggerFactory.getLogger(PaymentProcessedListener.class);

	private final PaymentResultService paymentResultService;

	PaymentProcessedListener(PaymentResultService paymentResultService) {
		this.paymentResultService = paymentResultService;
	}

	@RetryableTopic(attempts = "3",
			backOff = @BackOff(delayString = "${booking.consumer.retry.delay:1000}",
					multiplierString = "${booking.consumer.retry.multiplier:2.0}"),
			include = TransientBookingException.class,
			dltStrategy = DltStrategy.FAIL_ON_ERROR)
	@KafkaListener(topics = "payment-events", groupId = "booking-service-group")
	public void onPaymentProcessed(@Payload PaymentProcessedEvent event,
								   @Header(KafkaHeaders.RECEIVED_KEY) String key,
								   @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
								   @Header(KafkaHeaders.OFFSET) long offset) {
		log.info("Received PaymentProcessedEvent key={} partition={} offset={} eventId={} status={}",
				key, partition, offset, event.eventId(), event.status());
		paymentResultService.applyPaymentResult(event);
	}

	@DltHandler
	public void onDeadLetter(@Payload(required = false) PaymentProcessedEvent event,
							 @Header(KafkaHeaders.RECEIVED_KEY) String key,
							 @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
							 @Header(KafkaHeaders.OFFSET) long offset,
							 @Header(name = KafkaHeaders.EXCEPTION_MESSAGE, required = false) String reason) {
		log.error("PaymentProcessedEvent sent to DLT, seat may still be reserved: key={} partition={} offset={} eventId={} reason={}",
				key, partition, offset, event != null ? event.eventId() : null, reason);
	}
}
