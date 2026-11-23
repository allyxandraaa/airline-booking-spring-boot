package com.example.payment_service.grpc;

import com.example.grpc.payment.v1.PaymentServiceGrpc;
import com.example.grpc.payment.v1.RefundItem;
import com.example.grpc.payment.v1.RefundResult;
import com.example.grpc.payment.v1.RefundStatus;
import com.example.grpc.payment.v1.RefundSummary;
import com.example.payment_service.payment.PaymentService;
import com.example.payment_service.payment.PaymentStatus;
import com.example.payment_service.payment.RefundRequest;
import com.example.payment_service.payment.RefundResponse;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.grpc.server.service.GrpcService;

import java.math.BigDecimal;
import java.util.UUID;

@GrpcService
class PaymentGrpcService extends PaymentServiceGrpc.PaymentServiceImplBase {

	private static final Logger log = LoggerFactory.getLogger(PaymentGrpcService.class);

	private final PaymentService paymentService;

	PaymentGrpcService(PaymentService paymentService) {
		this.paymentService = paymentService;
	}

	@Override
	public StreamObserver<RefundItem> refundBatch(StreamObserver<RefundSummary> responseObserver) {
		return new StreamObserver<>() {
			private int total;
			private int succeeded;
			private int declined;
			private BigDecimal refunded = BigDecimal.ZERO;
			private boolean failed;

			@Override
			public void onNext(RefundItem item) {
				if (failed) {
					return;
				}
				try {
					RefundResponse response = refund(item);
					total++;
					if (response.status() == PaymentStatus.SUCCESS) {
						succeeded++;
						refunded = refunded.add(response.amount());
					} else {
						declined++;
					}
				} catch (RuntimeException e) {
					failed = true;
					responseObserver.onError(Status.fromThrowable(e).asRuntimeException());
				}
			}

			@Override
			public void onError(Throwable t) {
				log.warn("Refund batch interrupted by the client: {}", t.getMessage());
			}

			@Override
			public void onCompleted() {
				if (failed) {
					return;
				}
				responseObserver.onNext(RefundSummary.newBuilder()
						.setTotal(total)
						.setSucceeded(succeeded)
						.setDeclined(declined)
						.setRefundedAmount(MoneyMapper.toProto(refunded))
						.build());
				responseObserver.onCompleted();
			}
		};
	}

	@Override
	public StreamObserver<RefundItem> processRefunds(StreamObserver<RefundResult> responseObserver) {
		return new StreamObserver<>() {
			private boolean failed;

			@Override
			public void onNext(RefundItem item) {
				if (failed) {
					return;
				}
				try {
					responseObserver.onNext(toResult(refund(item)));
				} catch (RuntimeException e) {
					failed = true;
					responseObserver.onError(Status.fromThrowable(e).asRuntimeException());
				}
			}

			@Override
			public void onError(Throwable t) {
				log.warn("Refund stream interrupted by the client: {}", t.getMessage());
			}

			@Override
			public void onCompleted() {
				if (!failed) {
					responseObserver.onCompleted();
				}
			}
		};
	}

	private RefundResponse refund(RefundItem item) {
		if (item.getIdempotencyKey().isBlank()) {
			throw invalidArgument("Не вказано ключ ідемпотентності");
		}
		if (!item.hasAmount()) {
			throw invalidArgument("Не вказано суму");
		}
		BigDecimal amount = MoneyMapper.fromProto(item.getAmount());
		if (amount.signum() <= 0) {
			throw invalidArgument("Сума має бути додатною");
		}
		return paymentService.refund(item.getIdempotencyKey(), new RefundRequest(parseId(item.getBookingId()), amount));
	}

	private static RefundResult toResult(RefundResponse response) {
		RefundResult.Builder builder = RefundResult.newBuilder()
				.setBookingId(response.bookingId().toString())
				.setAmount(MoneyMapper.toProto(response.amount()))
				.setStatus(response.status() == PaymentStatus.SUCCESS
						? RefundStatus.REFUND_STATUS_SUCCESS
						: RefundStatus.REFUND_STATUS_DECLINED);
		if (response.paymentId() != null) {
			builder.setPaymentId(response.paymentId().toString());
		}
		return builder.build();
	}

	private static UUID parseId(String value) {
		try {
			return UUID.fromString(value);
		} catch (IllegalArgumentException _) {
			throw invalidArgument("Некоректний ідентифікатор: " + value);
		}
	}

	private static StatusRuntimeException invalidArgument(String message) {
		return Status.INVALID_ARGUMENT.withDescription(message).asRuntimeException();
	}
}
