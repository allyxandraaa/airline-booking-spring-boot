package com.example.overbooking_service.compensation.internal;

import com.example.grpc.payment.v1.PaymentServiceGrpc;
import com.example.grpc.payment.v1.RefundItem;
import com.example.grpc.payment.v1.RefundResult;
import com.example.grpc.payment.v1.RefundStatus;
import com.example.overbooking_service.compensation.CompensationResult;
import com.example.overbooking_service.grpc.MoneyMapper;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;

@Service
class RefundGatewayImpl implements RefundGateway {

	private static final Logger log = LoggerFactory.getLogger(RefundGatewayImpl.class);

	private final PaymentServiceGrpc.PaymentServiceStub paymentStub;

	RefundGatewayImpl(PaymentServiceGrpc.PaymentServiceStub paymentStub) {
		this.paymentStub = paymentStub;
	}

	@Override
	@CircuitBreaker(name = "paymentClient", fallbackMethod = "refundAllFallback")
	@Bulkhead(name = "paymentClient", type = Bulkhead.Type.SEMAPHORE)
	public List<CompensationResult> refundAll(List<RefundOrder> orders) {
		Map<UUID, RefundStatus> received = new ConcurrentHashMap<>();
		CompletableFuture<Void> finished = new CompletableFuture<>();
		StreamObserver<RefundItem> requests = paymentStub.processRefunds(new StreamObserver<>() {
			@Override
			public void onNext(RefundResult result) {
				received.put(UUID.fromString(result.getBookingId()), result.getStatus());
			}

			@Override
			public void onError(Throwable t) {
				finished.completeExceptionally(t);
			}

			@Override
			public void onCompleted() {
				finished.complete(null);
			}
		});
		orders.forEach(order -> requests.onNext(toItem(order)));
		requests.onCompleted();
		try {
			finished.join();
		} catch (CompletionException e) {
			throw new RefundStreamFailedException(Status.fromThrowable(e.getCause()), received);
		}
		return toResults(orders, received);
	}

	List<CompensationResult> refundAllFallback(List<RefundOrder> orders, Throwable ex) {
		log.warn("Refunds deferred: {}", ex.toString());
		return toResults(orders, ex instanceof RefundStreamFailedException failed ? failed.received : Map.of());
	}

	private static RefundItem toItem(RefundOrder order) {
		return RefundItem.newBuilder()
				.setBookingId(order.bookingId().toString())
				.setAmount(MoneyMapper.toProto(order.amount()))
				.setIdempotencyKey(order.idempotencyKey())
				.build();
	}

	private static List<CompensationResult> toResults(List<RefundOrder> orders, Map<UUID, RefundStatus> received) {
		return orders.stream()
				.map(order -> new CompensationResult(order.bookingId(), order.amount(),
						received.get(order.bookingId()) == RefundStatus.REFUND_STATUS_SUCCESS ? "COMPLETED" : "PENDING"))
				.toList();
	}

	private static final class RefundStreamFailedException extends StatusRuntimeException {

		private final Map<UUID, RefundStatus> received;

		RefundStreamFailedException(Status status, Map<UUID, RefundStatus> received) {
			super(status);
			this.received = received;
		}
	}
}
