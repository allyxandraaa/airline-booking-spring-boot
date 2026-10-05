package com.example.payment_service.payment.internal;

import com.example.payment_service.payment.PaymentService;
import com.example.payment_service.payment.PaymentStatus;
import com.example.payment_service.payment.PaymentType;
import com.example.payment_service.payment.RefundRequest;
import com.example.payment_service.payment.RefundResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;

@Service
class PaymentServiceImpl implements PaymentService {

	private final PaymentRepository paymentRepository;
	private final IdempotencyRecordRepository idempotencyRepository;
	private final TransactionTemplate transactionTemplate;
	private final JsonMapper jsonMapper;

	PaymentServiceImpl(PaymentRepository paymentRepository,
					   IdempotencyRecordRepository idempotencyRepository,
					   PlatformTransactionManager transactionManager,
					   JsonMapper jsonMapper) {
		this.paymentRepository = paymentRepository;
		this.idempotencyRepository = idempotencyRepository;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
		this.jsonMapper = jsonMapper;
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
