package com.example.payment_service.payment.internal;

import com.example.payment_service.payment.BookingReservedEvent;
import com.example.payment_service.payment.PaymentStatus;
import com.example.payment_service.payment.PaymentType;
import com.example.payment_service.payment.RefundRequest;
import com.example.payment_service.payment.RefundResponse;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DataJpaTest(properties = "DB_PASSWORD=unused")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PaymentPostgresTest {

	@ServiceConnection
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

	static {
		POSTGRES.start();
	}

	@Autowired
	PaymentRepository paymentRepository;

	@Autowired
	IdempotencyRecordRepository idempotencyRepository;

	@Autowired
	ProcessedEventRepository processedEventRepository;

	@Autowired
	OutboxEventRepository outboxRepository;

	@Autowired
	PlatformTransactionManager transactionManager;

	private PaymentServiceImpl service;

	@BeforeEach
	void setUp() {
		service = serviceWithDeclineProbability(0.0);
	}

	@AfterEach
	void cleanDatabase() {
		outboxRepository.deleteAll();
		processedEventRepository.deleteAll();
		idempotencyRepository.deleteAll();
		paymentRepository.deleteAll();
	}

	@Test
	void refundWithTheSameIdempotencyKeyCreatesOnePayment() {
		UUID bookingId = UUID.randomUUID();
		RefundRequest request = new RefundRequest(bookingId, new BigDecimal("120.50"));

		RefundResponse first = service.refund("key-1", request);
		RefundResponse second = service.refund("key-1", request);

		assertThat(second.paymentId()).isEqualTo(first.paymentId());
		assertThat(paymentRepository.findByBookingId(bookingId)).singleElement().satisfies(payment -> {
			assertThat(payment.getType()).isEqualTo(PaymentType.REFUND);
			assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
		});
		assertThat(idempotencyRepository.count()).isEqualTo(1);
	}

	@Test
	void refundsWithDifferentKeysAreSeparatePayments() {
		UUID bookingId = UUID.randomUUID();
		RefundRequest request = new RefundRequest(bookingId, new BigDecimal("50.00"));

		service.refund("key-a", request);
		service.refund("key-b", request);

		assertThat(paymentRepository.findByBookingId(bookingId)).hasSize(2);
		assertThat(idempotencyRepository.count()).isEqualTo(2);
	}

	@Test
	void bookingReservedEventCreatesPaymentOutboxEventAndProcessedMarkerTogether() {
		BookingReservedEvent event = BookingReservedEvent.now(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("200.00"));

		service.processBookingReserved(event);

		assertThat(paymentRepository.findByBookingId(event.bookingId())).singleElement().satisfies(payment -> {
			assertThat(payment.getType()).isEqualTo(PaymentType.CHARGE);
			assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
		});
		assertThat(outboxRepository.findTop50ByProcessedFalseOrderByCreatedAt()).hasSize(1);
		assertThat(processedEventRepository.existsById(event.eventId())).isTrue();
	}

	@Test
	void duplicateBookingReservedEventHasNoSecondEffect() {
		BookingReservedEvent event = BookingReservedEvent.now(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("200.00"));

		service.processBookingReserved(event);
		service.processBookingReserved(event);

		assertThat(paymentRepository.findByBookingId(event.bookingId())).hasSize(1);
		assertThat(outboxRepository.findTop50ByProcessedFalseOrderByCreatedAt()).hasSize(1);
		assertThat(processedEventRepository.count()).isEqualTo(1);
	}

	@Test
	void declinedPaymentIsStoredWithDeclinedStatus() {
		PaymentServiceImpl decliningService = serviceWithDeclineProbability(1.0);
		BookingReservedEvent event = BookingReservedEvent.now(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("75.00"));

		decliningService.processBookingReserved(event);

		assertThat(paymentRepository.findByBookingId(event.bookingId())).singleElement()
				.satisfies(payment -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.DECLINED));
	}

	private PaymentServiceImpl serviceWithDeclineProbability(double probability) {
		return new PaymentServiceImpl(paymentRepository, idempotencyRepository, processedEventRepository, outboxRepository,
				new ConsumerFailureSimulator(0), mock(OutboxTracing.class), transactionManager,
				JsonMapper.builder().build(), new SimpleMeterRegistry(), probability);
	}
}
