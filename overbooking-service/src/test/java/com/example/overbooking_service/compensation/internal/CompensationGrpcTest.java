package com.example.overbooking_service.compensation.internal;

import com.example.grpc.booking.v1.Booking;
import com.example.grpc.booking.v1.BookingServiceGrpc;
import com.example.grpc.booking.v1.BookingStatus;
import com.example.grpc.booking.v1.StreamBookingsRequest;
import com.example.grpc.payment.v1.PaymentServiceGrpc;
import com.example.grpc.payment.v1.RefundItem;
import com.example.grpc.payment.v1.RefundResult;
import com.example.grpc.payment.v1.RefundStatus;
import com.example.grpc.payment.v1.RefundSummary;
import com.example.overbooking_service.compensation.CompensationResult;
import com.example.overbooking_service.grpc.MoneyMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class CompensationGrpcTest {

	private static final BigDecimal AMOUNT = new BigDecimal("120.50");
	private static final FakeBookingService bookings = new FakeBookingService();
	private static final FakePaymentService payments = new FakePaymentService();
	private static Server bookingServer;
	private static Server paymentServer;

	@DynamicPropertySource
	static void channels(DynamicPropertyRegistry registry) throws IOException {
		bookingServer = ServerBuilder.forPort(0).addService(bookings).build().start();
		paymentServer = ServerBuilder.forPort(0).addService(payments).build().start();
		registry.add("spring.grpc.client.channel.booking.target", () -> "localhost:" + bookingServer.getPort());
		registry.add("spring.grpc.client.channel.payment.target", () -> "localhost:" + paymentServer.getPort());
	}

	@AfterAll
	static void stopServers() {
		bookingServer.shutdownNow();
		paymentServer.shutdownNow();
	}

	@Autowired
	MockMvc mockMvc;

	@Autowired
	RefundGateway refundGateway;

	@Autowired
	CircuitBreakerRegistry circuitBreakerRegistry;

	@BeforeEach
	void resetState() {
		bookings.reset();
		payments.reset();
		circuitBreakerRegistry.circuitBreaker("paymentClient").reset();
	}

	@Test
	void threeBookingsUseOneBookingStreamAndOneRefundStream() throws Exception {
		UUID[] ids = {UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()};

		mockMvc.perform(compensations(ids))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(3))
				.andExpect(jsonPath("$[0].bookingId").value(ids[0].toString()))
				.andExpect(jsonPath("$[0].amount").value(120.50))
				.andExpect(jsonPath("$[0].status").value("COMPLETED"))
				.andExpect(jsonPath("$[2].status").value("COMPLETED"));

		assertThat(bookings.calls).hasValue(1);
		assertThat(payments.calls).hasValue(1);
		assertThat(payments.items).extracting(RefundItem::getIdempotencyKey)
				.containsExactly(key(ids[0]), key(ids[1]), key(ids[2]));
	}

	@Test
	void declinedRefundIsReportedAsPending() throws Exception {
		UUID declined = UUID.randomUUID();
		payments.declined = Set.of(declined.toString());

		mockMvc.perform(compensations(UUID.randomUUID(), declined))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].status").value("COMPLETED"))
				.andExpect(jsonPath("$[1].status").value("PENDING"));
	}

	@Test
	void missingBookingReturns404WithoutRefunds() throws Exception {
		UUID missing = UUID.randomUUID();
		bookings.missing = Set.of(missing.toString());

		mockMvc.perform(compensations(UUID.randomUUID(), missing))
				.andExpect(status().isNotFound())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(404));

		assertThat(payments.calls).hasValue(0);
	}

	@Test
	void bookingStreamIsRetriedAfterTwoUnavailableResponses() throws Exception {
		bookings.failuresBeforeSuccess = 2;

		mockMvc.perform(compensations(UUID.randomUUID()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].status").value("COMPLETED"));

		assertThat(bookings.calls).hasValue(3);
	}

	@Test
	void unavailableBookingServiceReturns503AfterThreeAttempts() throws Exception {
		bookings.failuresBeforeSuccess = 100;

		mockMvc.perform(compensations(UUID.randomUUID()))
				.andExpect(status().isServiceUnavailable())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(503))
				.andExpect(jsonPath("$.title").isNotEmpty())
				.andExpect(jsonPath("$.timestamp").exists());

		assertThat(bookings.calls).hasValue(3);
		assertThat(payments.calls).hasValue(0);
	}

	@Test
	void refundStreamIsRetriedAfterTwoUnavailableResponses() {
		payments.failuresBeforeSuccess = 2;
		RefundOrder order = order();

		List<CompensationResult> results = refundGateway.refundAll(List.of(order));

		assertThat(results).containsExactly(new CompensationResult(order.bookingId(), AMOUNT, "COMPLETED"));
		assertThat(payments.calls).hasValue(3);
		assertThat(payments.items).extracting(RefundItem::getIdempotencyKey).containsExactly(order.idempotencyKey());
	}

	@Test
	void refundsStayPendingWhenPaymentServiceIsUnavailable() {
		payments.failuresBeforeSuccess = 100;

		List<CompensationResult> results = refundGateway.refundAll(List.of(order(), order()));

		assertThat(results).extracting(CompensationResult::status).containsExactly("PENDING", "PENDING");
		assertThat(payments.calls).hasValue(3);
	}

	@Test
	void resultsReceivedBeforeStreamFailureAreKept() {
		payments.failAfterResults = 1;
		RefundOrder first = order();

		List<CompensationResult> results = refundGateway.refundAll(List.of(first, order(), order()));

		assertThat(results).extracting(CompensationResult::status).containsExactly("COMPLETED", "PENDING", "PENDING");
	}

	@Test
	void errorStormOpensCircuitAndStopsCallingPaymentService() {
		payments.failuresBeforeSuccess = 1000;

		for (int call = 1; call <= 10; call++) {
			refundGateway.refundAll(List.of(order()));
		}

		assertThat(circuitBreakerRegistry.circuitBreaker("paymentClient").getState())
				.isEqualTo(CircuitBreaker.State.OPEN);
		int callsBefore = payments.calls.get();
		assertThat(refundGateway.refundAll(List.of(order()))).extracting(CompensationResult::status)
				.containsExactly("PENDING");
		assertThat(payments.calls).hasValue(callsBefore).hasValue(30);
	}

	@Test
	void summaryCountsRefundsOfThreeBookings() throws Exception {
		UUID declined = UUID.randomUUID();
		payments.declined = Set.of(declined.toString());
		UUID first = UUID.randomUUID();

		mockMvc.perform(summary(first, UUID.randomUUID(), declined))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.total").value(3))
				.andExpect(jsonPath("$.succeeded").value(2))
				.andExpect(jsonPath("$.declined").value(1))
				.andExpect(jsonPath("$.refundedAmount").value(241.0));

		assertThat(bookings.calls).hasValue(1);
		assertThat(payments.batchCalls).hasValue(1);
		assertThat(payments.items).first().extracting(RefundItem::getIdempotencyKey).isEqualTo(key(first));
	}

	@Test
	void summaryForMissingBookingReturns404() throws Exception {
		UUID missing = UUID.randomUUID();
		bookings.missing = Set.of(missing.toString());

		mockMvc.perform(summary(UUID.randomUUID(), missing))
				.andExpect(status().isNotFound())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

		assertThat(payments.batchCalls).hasValue(0);
	}

	@Test
	void refundBatchIsRetriedAfterTwoUnavailableResponses() throws Exception {
		payments.batchFailuresBeforeSuccess = 2;

		mockMvc.perform(summary(UUID.randomUUID()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.total").value(1));

		assertThat(payments.batchCalls).hasValue(3);
	}

	@Test
	void summaryReturns503WhenPaymentServiceIsUnavailable() throws Exception {
		payments.batchFailuresBeforeSuccess = 100;

		mockMvc.perform(summary(UUID.randomUUID()))
				.andExpect(status().isServiceUnavailable())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(503));

		assertThat(payments.batchCalls).hasValue(3);
	}

	private static RefundOrder order() {
		UUID bookingId = UUID.randomUUID();
		return new RefundOrder(bookingId, AMOUNT, key(bookingId));
	}

	private static String key(UUID bookingId) {
		return UUID.nameUUIDFromBytes(("refund:" + bookingId).getBytes(StandardCharsets.UTF_8)).toString();
	}

	private MockHttpServletRequestBuilder compensations(UUID... ids) {
		return request("/api/v1/overbooking/compensations", ids);
	}

	private MockHttpServletRequestBuilder summary(UUID... ids) {
		return request("/api/v1/overbooking/compensations/summary", ids);
	}

	private MockHttpServletRequestBuilder request(String path, UUID... ids) {
		String body = Arrays.stream(ids).map(id -> "\"" + id + "\"")
				.collect(Collectors.joining(",", "{\"bookingIds\":[", "]}"));
		return MockMvcRequestBuilders.post(path)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body);
	}

	private static final class FakeBookingService extends BookingServiceGrpc.BookingServiceImplBase {

		final AtomicInteger calls = new AtomicInteger();
		volatile int failuresBeforeSuccess;
		volatile Set<String> missing = Set.of();

		void reset() {
			calls.set(0);
			failuresBeforeSuccess = 0;
			missing = Set.of();
		}

		@Override
		public void streamBookings(StreamBookingsRequest request, StreamObserver<Booking> responseObserver) {
			if (calls.incrementAndGet() <= failuresBeforeSuccess) {
				responseObserver.onError(Status.UNAVAILABLE.asRuntimeException());
				return;
			}
			request.getBookingIdsList().stream()
					.filter(id -> !missing.contains(id))
					.forEach(id -> responseObserver.onNext(Booking.newBuilder()
							.setId(id)
							.setPriceAtBooking(MoneyMapper.toProto(AMOUNT))
							.setStatus(BookingStatus.BOOKING_STATUS_BUMPED)
							.build()));
			responseObserver.onCompleted();
		}
	}

	private static final class FakePaymentService extends PaymentServiceGrpc.PaymentServiceImplBase {

		final AtomicInteger calls = new AtomicInteger();
		final AtomicInteger batchCalls = new AtomicInteger();
		final List<RefundItem> items = new CopyOnWriteArrayList<>();
		volatile int failuresBeforeSuccess;
		volatile int batchFailuresBeforeSuccess;
		volatile int failAfterResults;
		volatile Set<String> declined = Set.of();

		void reset() {
			calls.set(0);
			batchCalls.set(0);
			items.clear();
			failuresBeforeSuccess = 0;
			batchFailuresBeforeSuccess = 0;
			failAfterResults = -1;
			declined = Set.of();
		}

		@Override
		public StreamObserver<RefundItem> refundBatch(StreamObserver<RefundSummary> responseObserver) {
			boolean rejected = batchCalls.incrementAndGet() <= batchFailuresBeforeSuccess;
			if (rejected) {
				responseObserver.onError(Status.UNAVAILABLE.asRuntimeException());
			}
			return new StreamObserver<>() {
				private int total;
				private int declinedCount;
				private BigDecimal refunded = BigDecimal.ZERO;

				@Override
				public void onNext(RefundItem item) {
					if (rejected) {
						return;
					}
					items.add(item);
					total++;
					if (declined.contains(item.getBookingId())) {
						declinedCount++;
					} else {
						refunded = refunded.add(MoneyMapper.fromProto(item.getAmount()));
					}
				}

				@Override
				public void onError(Throwable t) {
				}

				@Override
				public void onCompleted() {
					if (rejected) {
						return;
					}
					responseObserver.onNext(RefundSummary.newBuilder()
							.setTotal(total)
							.setSucceeded(total - declinedCount)
							.setDeclined(declinedCount)
							.setRefundedAmount(MoneyMapper.toProto(refunded))
							.build());
					responseObserver.onCompleted();
				}
			};
		}

		@Override
		public StreamObserver<RefundItem> processRefunds(StreamObserver<RefundResult> responseObserver) {
			boolean rejected = calls.incrementAndGet() <= failuresBeforeSuccess;
			if (rejected) {
				responseObserver.onError(Status.UNAVAILABLE.asRuntimeException());
			}
			return new StreamObserver<>() {
				private int sent;
				private boolean closed = rejected;

				@Override
				public void onNext(RefundItem item) {
					if (closed) {
						return;
					}
					if (sent == failAfterResults) {
						closed = true;
						responseObserver.onError(Status.UNAVAILABLE.asRuntimeException());
						return;
					}
					items.add(item);
					sent++;
					responseObserver.onNext(RefundResult.newBuilder()
							.setBookingId(item.getBookingId())
							.setPaymentId(UUID.randomUUID().toString())
							.setAmount(item.getAmount())
							.setStatus(declined.contains(item.getBookingId())
									? RefundStatus.REFUND_STATUS_DECLINED
									: RefundStatus.REFUND_STATUS_SUCCESS)
							.build());
				}

				@Override
				public void onError(Throwable t) {
				}

				@Override
				public void onCompleted() {
					if (!closed) {
						responseObserver.onCompleted();
					}
				}
			};
		}
	}
}
