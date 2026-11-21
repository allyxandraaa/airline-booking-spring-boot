package com.example.payment_service.grpc;

import com.example.grpc.payment.v1.PaymentServiceGrpc;
import com.example.grpc.payment.v1.RefundItem;
import com.example.grpc.payment.v1.RefundResult;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = "payment.grpc.failures-before-success=2")
@ActiveProfiles("test")
@AutoConfigureTestGrpcTransport
@ImportGrpcClients(types = PaymentServiceGrpc.PaymentServiceStub.class)
class PaymentGrpcFailureSimulationTest {

	@Autowired
	PaymentServiceGrpc.PaymentServiceStub stub;

	@MockitoBean
	PaymentService paymentService;

	@MockitoBean
	OutboxRelay outboxRelay;

	@Test
	void refundBatchFailsTwiceWithUnavailableThenSucceeds() throws Exception {
		when(paymentService.refund(anyString(), any(RefundRequest.class))).thenAnswer(invocation -> success(invocation.getArgument(1)));

		for (int attempt = 1; attempt <= 2; attempt++) {
			StreamCollector<RefundSummary> failed = new StreamCollector<>();
			send(stub.refundBatch(failed));
			assertThat(failed.await().errorCode()).isEqualTo(Status.Code.UNAVAILABLE);
		}
		StreamCollector<RefundSummary> succeeded = new StreamCollector<>();
		send(stub.refundBatch(succeeded));

		assertThat(succeeded.await().errorCode()).isNull();
		assertThat(succeeded.values().get(0).getTotal()).isEqualTo(1);
	}

	@Test
	void processRefundsFailsTwiceWithUnavailableThenSucceeds() throws Exception {
		when(paymentService.refund(anyString(), any(RefundRequest.class))).thenAnswer(invocation -> success(invocation.getArgument(1)));

		for (int attempt = 1; attempt <= 2; attempt++) {
			StreamCollector<RefundResult> failed = new StreamCollector<>();
			send(stub.processRefunds(failed));
			assertThat(failed.await().errorCode()).isEqualTo(Status.Code.UNAVAILABLE);
		}
		StreamCollector<RefundResult> succeeded = new StreamCollector<>();
		send(stub.processRefunds(succeeded));

		assertThat(succeeded.await().errorCode()).isNull();
		assertThat(succeeded.values()).hasSize(1);
	}

	private static boolean send(StreamObserver<RefundItem> requests) {
		try {
			requests.onNext(PaymentGrpcServiceTest.item("key", UUID.randomUUID().toString(), "10"));
			requests.onCompleted();
			return true;
		} catch (IllegalStateException _) {
			return false;
		}
	}

	private static RefundResponse success(RefundRequest request) {
		return new RefundResponse(UUID.randomUUID(), request.bookingId(), request.amount(), PaymentType.REFUND,
				PaymentStatus.SUCCESS);
	}
}
