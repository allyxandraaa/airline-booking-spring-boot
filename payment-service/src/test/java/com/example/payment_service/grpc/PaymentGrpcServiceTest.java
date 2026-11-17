package com.example.payment_service.grpc;

import com.example.grpc.common.v1.Money;
import com.example.grpc.payment.v1.PaymentServiceGrpc;
import com.example.grpc.payment.v1.RefundItem;
import com.example.grpc.payment.v1.RefundResult;
import com.example.grpc.payment.v1.RefundStatus;
import com.example.grpc.payment.v1.RefundSummary;
import com.example.payment_service.payment.PaymentService;
import com.example.payment_service.payment.PaymentStatus;
import com.example.payment_service.payment.PaymentType;
import com.example.payment_service.payment.RefundRequest;
import com.example.payment_service.payment.RefundResponse;
import com.example.payment_service.payment.internal.OutboxRelay;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.grpc.test.autoconfigure.AutoConfigureTestGrpcTransport;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.grpc.client.ImportGrpcClients;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureTestGrpcTransport
@ImportGrpcClients(types = PaymentServiceGrpc.PaymentServiceStub.class)
class PaymentGrpcServiceTest {

	@Autowired
	PaymentServiceGrpc.PaymentServiceStub stub;

	@MockitoBean
	PaymentService paymentService;

	@MockitoBean
	OutboxRelay outboxRelay;

	@Test
	void refundBatchReturnsOneSummaryAfterTheStreamIsClosed() throws Exception {
		stubRefunds();
		StreamCollector<RefundSummary> summary = new StreamCollector<>();

		StreamObserver<RefundItem> requests = stub.refundBatch(summary);
		requests.onNext(item("key-1", UUID.randomUUID().toString(), "10.50"));
		requests.onNext(item("key-2", UUID.randomUUID().toString(), "20"));
		requests.onNext(item("declined", UUID.randomUUID().toString(), "5"));
		requests.onCompleted();
		summary.await();

		assertThat(summary.values()).hasSize(1);
		RefundSummary result = summary.values().get(0);
		assertThat(result.getTotal()).isEqualTo(3);
		assertThat(result.getSucceeded()).isEqualTo(2);
		assertThat(result.getDeclined()).isEqualTo(1);
		assertThat(result.getRefundedAmount().getUnits()).isEqualTo(30);
		assertThat(result.getRefundedAmount().getNanos()).isEqualTo(500_000_000);
	}

	@Test
	void processRefundsAnswersEveryItemInOrder() throws Exception {
		stubRefunds();
		String first = UUID.randomUUID().toString();
		String second = UUID.randomUUID().toString();
		String third = UUID.randomUUID().toString();
		StreamCollector<RefundResult> results = new StreamCollector<>();

		StreamObserver<RefundItem> requests = stub.processRefunds(results);
		requests.onNext(item("key-1", first, "10"));
		requests.onNext(item("declined", second, "20"));
		requests.onNext(item("key-3", third, "30"));
		requests.onCompleted();
		results.await();

		assertThat(results.values()).extracting(RefundResult::getBookingId).containsExactly(first, second, third);
		assertThat(results.values()).extracting(RefundResult::getStatus).containsExactly(
				RefundStatus.REFUND_STATUS_SUCCESS, RefundStatus.REFUND_STATUS_DECLINED, RefundStatus.REFUND_STATUS_SUCCESS);
	}

	@Test
	void idempotencyKeyFromTheMessageReachesTheService() throws Exception {
		stubRefunds();
		UUID bookingId = UUID.randomUUID();
		StreamCollector<RefundResult> results = new StreamCollector<>();

		StreamObserver<RefundItem> requests = stub.processRefunds(results);
		requests.onNext(item("same-key", bookingId.toString(), "10"));
		requests.onNext(item("same-key", bookingId.toString(), "10"));
		requests.onCompleted();
		results.await();

		verify(paymentService, times(2)).refund(eq("same-key"), eq(new RefundRequest(bookingId, new BigDecimal("10"))));
	}

	@Test
	void nonPositiveAmountFailsBothMethodsWithInvalidArgument() throws Exception {
		StreamCollector<RefundSummary> batch = new StreamCollector<>();
		StreamObserver<RefundItem> batchRequests = stub.refundBatch(batch);
		batchRequests.onNext(item("key-1", UUID.randomUUID().toString(), "0"));
		batchRequests.onCompleted();
		batch.await();

		StreamCollector<RefundResult> stream = new StreamCollector<>();
		StreamObserver<RefundItem> streamRequests = stub.processRefunds(stream);
		streamRequests.onNext(item("key-2", UUID.randomUUID().toString(), "-5"));
		streamRequests.onCompleted();
		stream.await();

		assertThat(batch.errorCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
		assertThat(stream.errorCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
		verifyNoInteractions(paymentService);
	}

	@Test
	void malformedBookingIdFailsWithInvalidArgument() throws Exception {
		StreamCollector<RefundResult> results = new StreamCollector<>();

		StreamObserver<RefundItem> requests = stub.processRefunds(results);
		requests.onNext(item("key-1", "not-a-uuid", "10"));
		requests.onCompleted();
		results.await();

		assertThat(results.errorCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
		verifyNoInteractions(paymentService);
	}

	private void stubRefunds() {
		when(paymentService.refund(anyString(), any(RefundRequest.class))).thenAnswer(invocation -> {
			String key = invocation.getArgument(0);
			RefundRequest request = invocation.getArgument(1);
			PaymentStatus status = "declined".equals(key) ? PaymentStatus.DECLINED : PaymentStatus.SUCCESS;
			return new RefundResponse(UUID.randomUUID(), request.bookingId(), request.amount(), PaymentType.REFUND, status);
		});
	}

	static RefundItem item(String key, String bookingId, String amount) {
		BigDecimal value = new BigDecimal(amount);
		Money money = MoneyMapper.toProto(value);
		return RefundItem.newBuilder().setIdempotencyKey(key).setBookingId(bookingId).setAmount(money).build();
	}
}
