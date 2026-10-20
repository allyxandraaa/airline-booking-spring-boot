package com.example.payment_service.payment.internal;

import com.example.payment_service.payment.BookingReservedEvent;
import com.example.payment_service.payment.PaymentDto;
import com.example.payment_service.payment.PaymentProcessedEvent;
import com.example.payment_service.payment.PaymentProcessedStatus;
import com.example.payment_service.payment.PaymentService;
import com.example.payment_service.payment.PaymentStatus;
import com.example.payment_service.payment.PaymentType;
import com.example.payment_service.payment.RefundRequest;
import com.example.payment_service.payment.RefundResponse;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
class PaymentServiceImpl implements PaymentService {

	private static final Logger log = LoggerFactory.getLogger(PaymentServiceImpl.class);
	private static final String DECLINED_REASON = "Payment declined by simulation";

	private final PaymentRepository paymentRepository;
	private final IdempotencyRecordRepository idempotencyRepository;
	private final ProcessedEventRepository processedEventRepository;
	private final OutboxEventRepository outboxRepository;
	private final ConsumerFailureSimulator failureSimulator;
	private final TransactionTemplate transactionTemplate;
	private final JsonMapper jsonMapper;
	private final MeterRegistry registry;
	private final double declineProbability;

	PaymentServiceImpl(PaymentRepository paymentRepository,
					   IdempotencyRecordRepository idempotencyRepository,
					   ProcessedEventRepository processedEventRepository,
					   OutboxEventRepository outboxRepository,
					   ConsumerFailureSimulator failureSimulator,
					   PlatformTransactionManager transactionManager,
					   JsonMapper jsonMapper,
					   MeterRegistry registry,
					   @Value("${payment.decline-probability:0.1}") double declineProbability) {
		this.paymentRepository = paymentRepository;
		this.idempotencyRepository = idempotencyRepository;
		this.processedEventRepository = processedEventRepository;
		this.outboxRepository = outboxRepository;
		this.failureSimulator = failureSimulator;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
		this.jsonMapper = jsonMapper;
		this.registry = registry;
		this.declineProbability = declineProbability;
	}

	@Override
	public RefundResponse refund(String idempotencyKey, RefundRequest request) {
		try {
			return transactionTemplate.execute(status -> processRefund(idempotencyKey, request));
		} catch (DataIntegrityViolationException e) {
			return idempotencyRepository.findById(idempotencyKey)
					.map(this::readResponse)
					.orElseThrow(() -> e);
		}
	}

	@Override
	public void processBookingReserved(BookingReservedEvent event) {
		failureSimulator.failIfConfigured();
		try {
			transactionTemplate.executeWithoutResult(status -> charge(event));
		} catch (DataIntegrityViolationException e) {
			log.info("Event {} already processed concurrently: duplicate, skip", event.eventId());
		}
	}

	@Override
	public void recordProcessingFailure(UUID bookingId, String reason) {
		transactionTemplate.executeWithoutResult(status ->
				saveResult(bookingId, null, PaymentProcessedStatus.FAILED, reason));
	}

	@Override
	public List<PaymentDto> findByBookingId(UUID bookingId) {
		return paymentRepository.findByBookingId(bookingId).stream()
				.map(payment -> new PaymentDto(payment.getId(), payment.getBookingId(), payment.getPaymentMethodId(),
						payment.getAmount(), payment.getType(), payment.getStatus()))
				.toList();
	}

	private void charge(BookingReservedEvent event) {
		if (processedEventRepository.existsById(event.eventId())) {
			log.info("Event {} already processed: duplicate, skip", event.eventId());
			return;
		}
		boolean declined = ThreadLocalRandom.current().nextDouble() < declineProbability;
		Payment payment = paymentRepository.save(new Payment(event.bookingId(), event.paymentMethodId(),
				event.amount(), declined ? PaymentStatus.DECLINED : PaymentStatus.SUCCESS, PaymentType.CHARGE));
		processedEventRepository.saveAndFlush(new ProcessedEvent(event.eventId(), Instant.now()));
		saveResult(event.bookingId(), payment.getId(),
				declined ? PaymentProcessedStatus.DECLINED : PaymentProcessedStatus.SUCCESS,
				declined ? DECLINED_REASON : null);
		Counter.builder("payments.processed.total")
				.description("Оброблені платежі за статусом")
				.tag("status", payment.getStatus().name())
				.register(registry)
				.increment();
		log.info("Payment {} for booking {} is {}", payment.getId(), event.bookingId(), payment.getStatus());
	}

	private void saveResult(UUID bookingId, UUID paymentId, PaymentProcessedStatus status, String reason) {
		UUID eventId = UUID.randomUUID();
		PaymentProcessedEvent result = new PaymentProcessedEvent(eventId, bookingId, paymentId, status, reason, Instant.now());
		outboxRepository.save(new OutboxEvent(eventId, "Payment", bookingId, "PaymentProcessed",
				jsonMapper.writeValueAsString(result), result.occurredAt()));
	}

	private RefundResponse processRefund(String idempotencyKey, RefundRequest request) {
		return idempotencyRepository.findById(idempotencyKey)
				.map(this::readResponse)
				.orElseGet(() -> createRefund(idempotencyKey, request));
	}

	private RefundResponse createRefund(String idempotencyKey, RefundRequest request) {
		Payment payment = paymentRepository.save(new Payment(
				request.bookingId(), request.amount(), PaymentStatus.SUCCESS, PaymentType.REFUND));
		RefundResponse response = new RefundResponse(
				payment.getId(), payment.getBookingId(), payment.getAmount(), payment.getType(), payment.getStatus());
		idempotencyRepository.saveAndFlush(
				new IdempotencyRecord(idempotencyKey, jsonMapper.writeValueAsString(response), Instant.now()));
		return response;
	}

	private RefundResponse readResponse(IdempotencyRecord record) {
		return jsonMapper.readValue(record.getResponseBody(), RefundResponse.class);
	}
}
