package com.example.overbooking_service.compensation.internal;

import com.example.grpc.payment.v1.PaymentServiceGrpc;
import com.example.grpc.payment.v1.RefundItem;
import com.example.grpc.payment.v1.RefundResult;
import com.example.grpc.payment.v1.RefundStatus;
import com.example.overbooking_service.compensation.CompensationResult;
import com.example.overbooking_service.resolution.OverbookingResolutionService;
import eu.rekawek.toxiproxy.Proxy;
import eu.rekawek.toxiproxy.ToxiproxyClient;
import eu.rekawek.toxiproxy.model.ToxicDirection;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.Testcontainers;
import org.testcontainers.toxiproxy.ToxiproxyContainer;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class PaymentLatencyToxiproxyTest {

	private static final int PROXY_PORT = 8666;
	private static final String COMPLETED = "COMPLETED";
	private static final String PENDING = "PENDING";
	private static final FakePaymentService payment = new FakePaymentService();
	private static Server paymentServer;
	private static ToxiproxyContainer toxiproxy;
	private static Proxy proxy;

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) throws IOException {
		paymentServer = ServerBuilder.forPort(0).addService(payment).build().start();
		Testcontainers.exposeHostPorts(paymentServer.getPort());
		toxiproxy = new ToxiproxyContainer("ghcr.io/shopify/toxiproxy:2.12.0");
		toxiproxy.start();
		proxy = new ToxiproxyClient(toxiproxy.getHost(), toxiproxy.getControlPort())
				.createProxy("payment", "0.0.0.0:" + PROXY_PORT, "host.testcontainers.internal:" + paymentServer.getPort());
		registry.add("spring.grpc.client.channel.payment.target",
				() -> toxiproxy.getHost() + ":" + toxiproxy.getMappedPort(PROXY_PORT));
		registry.add("spring.grpc.client.channel.payment.default.deadline", () -> "1s");
		registry.add("resilience4j.circuitbreaker.instances.paymentClient.sliding-window-size", () -> "4");
		registry.add("resilience4j.circuitbreaker.instances.paymentClient.minimum-number-of-calls", () -> "4");
		registry.add("resilience4j.circuitbreaker.instances.paymentClient.wait-duration-in-open-state", () -> "30s");
	}

	@AfterAll
	static void stopInfrastructure() {
		toxiproxy.stop();
		paymentServer.shutdownNow();
	}

	@MockitoBean
	OverbookingResolutionService resolutionService;

	@Autowired
	RefundGateway refundGateway;

	@Autowired
	CircuitBreakerRegistry circuitBreakerRegistry;

	@BeforeEach
	void resetCircuitBreaker() {
		circuitBreakerRegistry.circuitBreaker("paymentClient").reset();
	}

	@Test
	void networkLatencyOpensCircuitBreakerAndFallbackReplacesFailure() throws Exception {
		List<RefundOrder> orders = List.of(new RefundOrder(UUID.randomUUID(), new BigDecimal("100.00"), "latency-key"));

		assertThat(statuses(refundGateway.refundAll(orders))).containsExactly(COMPLETED);

		proxy.toxics().latency("slow-network", ToxicDirection.DOWNSTREAM, 3000);

		for (int attempt = 0; attempt < 4; attempt++) {
			assertThat(statuses(refundGateway.refundAll(orders))).containsExactly(PENDING);
		}
		assertThat(circuitBreakerRegistry.circuitBreaker("paymentClient").getState()).isEqualTo(CircuitBreaker.State.OPEN);

		int callsWhileOpen = payment.calls.get();
		long started = System.nanoTime();
		assertThat(statuses(refundGateway.refundAll(orders))).containsExactly(PENDING);
		assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofMillis(500));
		assertThat(payment.calls.get()).isEqualTo(callsWhileOpen);

		proxy.toxics().get("slow-network").remove();
	}

	private static List<String> statuses(List<CompensationResult> results) {
		return results.stream().map(CompensationResult::status).toList();
	}

	private static final class FakePaymentService extends PaymentServiceGrpc.PaymentServiceImplBase {

		final AtomicInteger calls = new AtomicInteger();

		@Override
		public StreamObserver<RefundItem> processRefunds(StreamObserver<RefundResult> responseObserver) {
			calls.incrementAndGet();
			return new StreamObserver<>() {
				private boolean aborted;

				@Override
				public void onNext(RefundItem item) {
					responseObserver.onNext(RefundResult.newBuilder()
							.setBookingId(item.getBookingId())
							.setAmount(item.getAmount())
							.setStatus(RefundStatus.REFUND_STATUS_SUCCESS)
							.build());
				}

				@Override
				public void onError(Throwable t) {
					aborted = true;
				}

				@Override
				public void onCompleted() {
					if (!aborted) {
						responseObserver.onCompleted();
					}
				}
			};
		}
	}
}
