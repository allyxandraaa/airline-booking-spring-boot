package com.example.payment_service.payment.internal;

import com.example.payment_service.payment.PaymentStatus;
import com.example.payment_service.payment.PaymentType;
import com.example.payment_service.payment.RefundRequest;
import com.example.payment_service.payment.RefundResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentServiceTest {

	private final PaymentRepository paymentRepository = mock(PaymentRepository.class);
	private final IdempotencyRecordRepository idempotencyRepository = mock(IdempotencyRecordRepository.class);
	private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
	private final JsonMapper jsonMapper = JsonMapper.builder().build();
	private final PaymentServiceImpl service = new PaymentServiceImpl(
			paymentRepository, idempotencyRepository, transactionManager, jsonMapper);
	private final Map<String, IdempotencyRecord> store = new HashMap<>();

	@BeforeEach
	void setUp() {
		when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
			Payment payment = invocation.getArgument(0);
			ReflectionTestUtils.setField(payment, "id", UUID.randomUUID());
			return payment;
		});
		when(idempotencyRepository.findById(anyString()))
				.thenAnswer(invocation -> Optional.ofNullable(store.get(invocation.<String>getArgument(0))));
		when(idempotencyRepository.saveAndFlush(any(IdempotencyRecord.class))).thenAnswer(invocation -> {
			IdempotencyRecord record = invocation.getArgument(0);
			store.put(record.getId(), record);
			return record;
		});
	}

	private RefundRequest request() {
		return new RefundRequest(UUID.randomUUID(), new BigDecimal("120.50"));
	}

	@Test
	void newKeyCreatesPaymentAndStoresResponse() {
		RefundRequest request = request();

		RefundResponse response = service.refund("key-1", request);

		assertThat(response.paymentId()).isNotNull();
		assertThat(response.bookingId()).isEqualTo(request.bookingId());
		assertThat(response.amount()).isEqualByComparingTo("120.50");
		assertThat(response.type()).isEqualTo(PaymentType.REFUND);
		assertThat(response.status()).isEqualTo(PaymentStatus.SUCCESS);
		verify(paymentRepository, times(1)).save(any(Payment.class));
		assertThat(store).containsKey("key-1");
	}

	@Test
	void sameKeyReturnsStoredResponseWithoutCreatingSecondPayment() {
		RefundRequest request = request();

		RefundResponse first = service.refund("key-1", request);
		RefundResponse second = service.refund("key-1", request);

		assertThat(second).isEqualTo(first);
		assertThat(second.paymentId()).isEqualTo(first.paymentId());
		verify(paymentRepository, times(1)).save(any(Payment.class));
	}

	@Test
	void differentKeysCreateTwoPayments() {
		RefundResponse first = service.refund("key-1", request());
		RefundResponse second = service.refund("key-2", request());

		assertThat(second.paymentId()).isNotEqualTo(first.paymentId());
		verify(paymentRepository, times(2)).save(any(Payment.class));
		assertThat(store).containsKeys("key-1", "key-2");
	}

	@Test
	void concurrentDuplicateReturnsStoredResponseOfWinner() {
		RefundResponse winner = new RefundResponse(UUID.randomUUID(), UUID.randomUUID(),
				new BigDecimal("120.50"), PaymentType.REFUND, PaymentStatus.SUCCESS);
		IdempotencyRecord winnerRecord = new IdempotencyRecord(
				"key-1", jsonMapper.writeValueAsString(winner), Instant.now());
		when(idempotencyRepository.findById("key-1"))
				.thenReturn(Optional.empty(), Optional.of(winnerRecord));
		doThrow(new DataIntegrityViolationException("duplicate key"))
				.when(idempotencyRepository).saveAndFlush(any(IdempotencyRecord.class));

		RefundResponse response = service.refund("key-1", request());

		assertThat(response).isEqualTo(winner);
	}
}
