package com.example.booking_service.integration;

import com.example.booking_service.booking.BookingService;
import com.example.booking_service.booking.PaymentResultService;
import com.example.booking_service.booking.TicketClass;
import com.example.booking_service.grpc.MoneyMapper;
import com.example.grpc.flight.v1.FlightServiceGrpc;
import com.example.grpc.flight.v1.GetPriceRequest;
import com.example.grpc.flight.v1.GetPriceResponse;
import com.example.grpc.flight.v1.ReleaseSeatRequest;
import com.example.grpc.flight.v1.ReleaseSeatResponse;
import com.example.grpc.flight.v1.ReserveSeatRequest;
import com.example.grpc.flight.v1.ReserveSeatResponse;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
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

import java.io.IOException;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "spring.grpc.server.port=0")
@ActiveProfiles("test")
class FlightGatewayGrpcTest {

	private static final FakeFlightService fake = new FakeFlightService();
	private static Server server;

	@DynamicPropertySource
	static void flightChannel(DynamicPropertyRegistry registry) throws IOException {
		server = ServerBuilder.forPort(0).addService(fake).build().start();
		registry.add("spring.grpc.client.channel.flight.target", () -> "localhost:" + server.getPort());
	}

	@AfterAll
	static void stopServer() {
		server.shutdownNow();
	}

	@Autowired
	FlightGateway gateway;

	@MockitoBean
	BookingService bookingService;

	@MockitoBean
	PaymentResultService paymentResultService;

	private final UUID flightId = UUID.randomUUID();

	@BeforeEach
	void resetFake() {
		fake.reset();
	}

	@Test
	void getPriceSendsTicketClassAndConvertsMoney() {
		BigDecimal price = gateway.getPrice(flightId, TicketClass.BUSINESS);

		assertThat(price).isEqualByComparingTo(new BigDecimal("312.50"));
		assertThat(fake.lastPriceRequest.getFlightId()).isEqualTo(flightId.toString());
		assertThat(fake.lastPriceRequest.getTicketClass())
				.isEqualTo(com.example.grpc.flight.v1.TicketClass.TICKET_CLASS_BUSINESS);
	}

	@Test
	void getPriceIsRetriedAfterTwoUnavailableResponses() {
		fake.failuresBeforeSuccess = 2;

		BigDecimal price = gateway.getPrice(flightId, TicketClass.ECONOMY);

		assertThat(price).isEqualByComparingTo(new BigDecimal("312.50"));
		assertThat(fake.getPriceCalls).hasValue(3);
	}

	@Test
	void getPriceGivesUpAfterThreeAttempts() {
		fake.failuresBeforeSuccess = 10;

		assertThatThrownBy(() -> gateway.getPrice(flightId, TicketClass.ECONOMY))
				.isInstanceOfSatisfying(StatusRuntimeException.class,
						e -> assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.UNAVAILABLE));
		assertThat(fake.getPriceCalls).hasValue(3);
	}

	@Test
	void reserveSeatIsNeverRetried() {
		fake.failuresBeforeSuccess = 10;

		assertThatThrownBy(() -> gateway.reserveSeat(flightId, TicketClass.ECONOMY))
				.isInstanceOfSatisfying(StatusRuntimeException.class,
						e -> assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.UNAVAILABLE));
		assertThat(fake.reserveCalls).hasValue(1);
	}

	@Test
	void unknownFlightBecomesFlightNotFoundWithoutRetry() {
		fake.failure = Status.NOT_FOUND;
		fake.failuresBeforeSuccess = 10;

		assertThatThrownBy(() -> gateway.getPrice(flightId, TicketClass.ECONOMY))
				.isInstanceOf(FlightNotFoundException.class);
		assertThat(fake.getPriceCalls).hasValue(1);
	}

	@Test
	void noFreeSeatsBecomesSeatUnavailable() {
		fake.failure = Status.FAILED_PRECONDITION;
		fake.failuresBeforeSuccess = 10;

		assertThatThrownBy(() -> gateway.reserveSeat(flightId, TicketClass.ECONOMY))
				.isInstanceOf(SeatUnavailableException.class);
	}

	@Test
	void reserveSeatReturnsReservationId() {
		assertThat(gateway.reserveSeat(flightId, TicketClass.BUSINESS)).isEqualTo(fake.reservationId);
	}

	@Test
	void releaseSeatCallsFlightService() {
		UUID reservationId = UUID.randomUUID();

		gateway.releaseSeat(reservationId);

		assertThat(fake.lastReleaseRequest.getReservationId()).isEqualTo(reservationId.toString());
	}

	private static final class FakeFlightService extends FlightServiceGrpc.FlightServiceImplBase {

		final AtomicInteger getPriceCalls = new AtomicInteger();
		final AtomicInteger reserveCalls = new AtomicInteger();
		final UUID reservationId = UUID.randomUUID();
		volatile int failuresBeforeSuccess;
		volatile Status failure = Status.UNAVAILABLE;
		volatile GetPriceRequest lastPriceRequest;
		volatile ReleaseSeatRequest lastReleaseRequest;

		void reset() {
			getPriceCalls.set(0);
			reserveCalls.set(0);
			failuresBeforeSuccess = 0;
			failure = Status.UNAVAILABLE;
		}

		@Override
		public void getPrice(GetPriceRequest request, StreamObserver<GetPriceResponse> responseObserver) {
			lastPriceRequest = request;
			if (getPriceCalls.incrementAndGet() <= failuresBeforeSuccess) {
				responseObserver.onError(failure.asRuntimeException());
				return;
			}
			responseObserver.onNext(GetPriceResponse.newBuilder()
					.setPrice(MoneyMapper.toProto(new BigDecimal("312.50")))
					.build());
			responseObserver.onCompleted();
		}

		@Override
		public void reserveSeat(ReserveSeatRequest request, StreamObserver<ReserveSeatResponse> responseObserver) {
			if (reserveCalls.incrementAndGet() <= failuresBeforeSuccess) {
				responseObserver.onError(failure.asRuntimeException());
				return;
			}
			responseObserver.onNext(ReserveSeatResponse.newBuilder().setReservationId(reservationId.toString()).build());
			responseObserver.onCompleted();
		}

		@Override
		public void releaseSeat(ReleaseSeatRequest request, StreamObserver<ReleaseSeatResponse> responseObserver) {
			lastReleaseRequest = request;
			responseObserver.onNext(ReleaseSeatResponse.getDefaultInstance());
			responseObserver.onCompleted();
		}
	}
}
