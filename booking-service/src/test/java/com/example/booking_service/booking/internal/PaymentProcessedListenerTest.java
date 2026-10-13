package com.example.booking_service.booking.internal;

import com.example.booking_service.booking.BookingService;
import com.example.booking_service.booking.PaymentProcessedEvent;
import com.example.booking_service.booking.PaymentProcessedStatus;
import com.example.booking_service.booking.PaymentResultService;
import com.example.booking_service.exception.TransientBookingException;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest(properties = {
		"spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
		"spring.kafka.listener.auto-startup=true",
		"spring.kafka.admin.auto-create=true",
		"booking.consumer.retry.delay=100"
})
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 3, topics = "payment-events")
class PaymentProcessedListenerTest {

	private static final long TIMEOUT = 15_000;

	@Autowired
	KafkaTemplate<String, Object> kafkaTemplate;

	@Autowired
	EmbeddedKafkaBroker broker;

	@MockitoBean
	PaymentResultService paymentResultService;

	@MockitoBean
	BookingService bookingService;

	private PaymentProcessedEvent send(PaymentProcessedStatus status) {
		PaymentProcessedEvent event = PaymentProcessedEvent.now(UUID.randomUUID(), UUID.randomUUID(), status, null);
		kafkaTemplate.send("payment-events", event.bookingId().toString(), event);
		return event;
	}

	@Test
	void eventIsDeliveredToPaymentResultService() {
		PaymentProcessedEvent event = send(PaymentProcessedStatus.SUCCESS);

		verify(paymentResultService, timeout(TIMEOUT)).applyPaymentResult(event);
	}

	@Test
	void transientFailureIsRetriedThenSentToDlt() {
		Consumer<String, String> dltConsumer = new DefaultKafkaConsumerFactory<>(
				KafkaTestUtils.consumerProps(broker, "dlt-test", true),
				new StringDeserializer(), new StringDeserializer()).createConsumer();
		dltConsumer.subscribe(List.of("payment-events-dlt"));
		PaymentProcessedEvent event = PaymentProcessedEvent.now(UUID.randomUUID(), null, PaymentProcessedStatus.DECLINED, null);
		doThrow(new TransientBookingException("flight service down", null))
				.when(paymentResultService).applyPaymentResult(event);

		kafkaTemplate.send("payment-events", event.bookingId().toString(), event);

		verify(paymentResultService, timeout(TIMEOUT).times(3)).applyPaymentResult(event);
		ConsumerRecord<String, String> dead = KafkaTestUtils.getSingleRecord(dltConsumer, "payment-events-dlt", Duration.ofMillis(TIMEOUT));
		assertThat(dead.key()).isEqualTo(event.bookingId().toString());
		assertThat(dead.value()).contains(event.eventId().toString());
		dltConsumer.close();
	}
}
