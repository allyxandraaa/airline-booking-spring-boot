package com.example.flight_service.grpc;

import com.example.flight_service.exception.FlightNotFoundException;
import com.example.flight_service.exception.ReservationNotFoundException;
import com.example.flight_service.exception.SeatUnavailableException;
import com.example.flight_service.flight.FlightService;
import com.example.flight_service.flight.ReservationDto;
import com.example.grpc.flight.v1.FlightServiceGrpc;
import com.example.grpc.flight.v1.GetPriceRequest;
import com.example.grpc.flight.v1.ReleaseSeatRequest;
import com.example.grpc.flight.v1.ReserveSeatRequest;
import com.example.grpc.flight.v1.TicketClass;
import com.google.rpc.ErrorInfo;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.protobuf.StatusProto;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureTestGrpcTransport
@ImportGrpcClients(types = FlightServiceGrpc.FlightServiceBlockingStub.class)
class FlightGrpcServiceTest {

	private static final UUID FLIGHT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
	private static final UUID RESERVATION_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

	@Autowired
	FlightServiceGrpc.FlightServiceBlockingStub stub;

	@MockitoBean
	FlightService flightService;

	@Test
	void getPriceReturnsMoney() {
		when(flightService.calculatePrice(FLIGHT_ID, com.example.flight_service.flight.TicketClass.ECONOMY))
				.thenReturn(new BigDecimal("129.50"));

		var response = stub.getPrice(priceRequest(TicketClass.TICKET_CLASS_ECONOMY));

		assertThat(response.getPrice().getUnits()).isEqualTo(129);
		assertThat(response.getPrice().getNanos()).isEqualTo(500_000_000);
	}

	@Test
	void reserveSeatReturnsReservationId() {
		when(flightService.reserveSeat(FLIGHT_ID, com.example.flight_service.flight.TicketClass.BUSINESS))
				.thenReturn(new ReservationDto(RESERVATION_ID));

		var response = stub.reserveSeat(ReserveSeatRequest.newBuilder()
				.setFlightId(FLIGHT_ID.toString())
				.setTicketClass(TicketClass.TICKET_CLASS_BUSINESS)
				.build());

		assertThat(response.getReservationId()).isEqualTo(RESERVATION_ID.toString());
	}

	@Test
	void releaseSeatDelegatesToService() {
		stub.releaseSeat(ReleaseSeatRequest.newBuilder().setReservationId(RESERVATION_ID.toString()).build());

		verify(flightService).releaseSeat(RESERVATION_ID);
	}

	@Test
	void unknownFlightReturnsNotFound() throws Exception {
		when(flightService.calculatePrice(FLIGHT_ID, com.example.flight_service.flight.TicketClass.ECONOMY))
				.thenThrow(new FlightNotFoundException(FLIGHT_ID));

		StatusRuntimeException error = failureOf(() -> stub.getPrice(priceRequest(TicketClass.TICKET_CLASS_ECONOMY)));

		assertThat(error.getStatus().getCode()).isEqualTo(Status.Code.NOT_FOUND);
		assertThat(reasonOf(error)).isEqualTo("FLIGHT_NOT_FOUND");
	}

	@Test
	void unknownReservationReturnsNotFound() throws Exception {
		doThrow(new ReservationNotFoundException(RESERVATION_ID)).when(flightService).releaseSeat(RESERVATION_ID);

		StatusRuntimeException error = failureOf(() ->
				stub.releaseSeat(ReleaseSeatRequest.newBuilder().setReservationId(RESERVATION_ID.toString()).build()));

		assertThat(error.getStatus().getCode()).isEqualTo(Status.Code.NOT_FOUND);
		assertThat(reasonOf(error)).isEqualTo("RESERVATION_NOT_FOUND");
	}

	@Test
	void noFreeSeatsReturnsFailedPrecondition() throws Exception {
		when(flightService.reserveSeat(FLIGHT_ID, com.example.flight_service.flight.TicketClass.ECONOMY))
				.thenThrow(new SeatUnavailableException(FLIGHT_ID, com.example.flight_service.flight.TicketClass.ECONOMY));

		StatusRuntimeException error = failureOf(() -> stub.reserveSeat(ReserveSeatRequest.newBuilder()
				.setFlightId(FLIGHT_ID.toString())
				.setTicketClass(TicketClass.TICKET_CLASS_ECONOMY)
				.build()));

		assertThat(error.getStatus().getCode()).isEqualTo(Status.Code.FAILED_PRECONDITION);
		assertThat(reasonOf(error)).isEqualTo("SEATS_UNAVAILABLE");
	}

	@Test
	void malformedFlightIdReturnsInvalidArgument() {
		StatusRuntimeException error = failureOf(() -> stub.getPrice(GetPriceRequest.newBuilder()
				.setFlightId("not-a-uuid")
				.setTicketClass(TicketClass.TICKET_CLASS_ECONOMY)
				.build()));

		assertThat(error.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
	}

	@Test
	void unspecifiedTicketClassReturnsInvalidArgument() {
		StatusRuntimeException error = failureOf(() -> stub.getPrice(priceRequest(TicketClass.TICKET_CLASS_UNSPECIFIED)));

		assertThat(error.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
	}

	private static GetPriceRequest priceRequest(TicketClass ticketClass) {
		return GetPriceRequest.newBuilder().setFlightId(FLIGHT_ID.toString()).setTicketClass(ticketClass).build();
	}

	private static StatusRuntimeException failureOf(Runnable call) {
		try {
			call.run();
		} catch (StatusRuntimeException e) {
			return e;
		}
		throw new AssertionError("Очікувався StatusRuntimeException");
	}

	private static String reasonOf(StatusRuntimeException error) throws Exception {
		return StatusProto.fromThrowable(error).getDetails(0).unpack(ErrorInfo.class).getReason();
	}
}
