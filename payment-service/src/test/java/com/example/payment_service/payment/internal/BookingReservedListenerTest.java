package com.example.payment_service.payment.internal;

import com.example.payment_service.payment.BookingReservedEvent;
import com.example.payment_service.payment.PaymentService;
import com.example.payment_service.payment.TransientPaymentException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest(properties = {
		"spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
		"spring.kafka.listener.auto-startup=true",
		"spring.kafka.admin.auto-create=true",
		"payment.consumer.retry.delay=100"
})
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 3, topics = "booking-events")
class BookingReservedListenerTest {

	private static final long TIMEOUT = 15_000;

	@Autowired
	KafkaTemplate<String, Object> kafkaTemplate;

	@MockitoBean
	PaymentService paymentService;

	@MockitoBean
	OutboxRelay outboxRelay;

	private BookingReservedEvent send(Object payload, UUID bookingId) {
		kafkaTemplate.send("booking-events", bookingId.toString(), payload);
		return payload instanceof BookingReservedEvent event ? event : null;
	}

	private BookingReservedEvent event(UUID bookingId) {
		return BookingReservedEvent.now(bookingId, UUID.randomUUID(), new BigDecimal("120.50"));
	}

	@Test
	void eventIsDeliveredToPaymentService() {
		UUID bookingId = UUID.randomUUID();
		BookingReservedEvent event = send(event(bookingId), bookingId);

		verify(paymentService, timeout(TIMEOUT)).processBookingReserved(event);
	}

	@Test
	void transientFailureIsRetriedThenSentToDltWithFailedResult() {
		UUID bookingId = UUID.randomUUID();
		BookingReservedEvent event = event(bookingId);
		doThrow(new TransientPaymentException("db down")).when(paymentService).processBookingReserved(event);

		send(event, bookingId);

		verify(paymentService, timeout(TIMEOUT).times(3)).processBookingReserved(event);
		verify(paymentService, timeout(TIMEOUT)).recordProcessingFailure(eq(bookingId), contains("db down"));
	}

	@Test
	void nonTransientFailureGoesStraightToDlt() {
		UUID bookingId = UUID.randomUUID();
		BookingReservedEvent event = event(bookingId);
		doThrow(new IllegalStateException("bug")).when(paymentService).processBookingReserved(event);

		send(event, bookingId);

		verify(paymentService, timeout(TIMEOUT)).recordProcessingFailure(eq(bookingId), contains("bug"));
		verify(paymentService, times(1)).processBookingReserved(event);
	}

	@Test
	void malformedMessageDoesNotBlockFollowingEvents() {
		send("not a booking event", UUID.randomUUID());
		UUID bookingId = UUID.randomUUID();
		BookingReservedEvent event = send(event(bookingId), bookingId);

		verify(paymentService, timeout(TIMEOUT)).processBookingReserved(event);
		verify(paymentService, times(1)).processBookingReserved(any());
	}
}
