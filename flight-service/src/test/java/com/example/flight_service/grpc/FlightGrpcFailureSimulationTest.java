package com.example.flight_service.grpc;

import com.example.flight_service.flight.FlightService;
import com.example.grpc.flight.v1.FlightServiceGrpc;
import com.example.grpc.flight.v1.GetPriceRequest;
import com.example.grpc.flight.v1.TicketClass;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = "flight.grpc.failures-before-success=2")
@ActiveProfiles("test")
@AutoConfigureTestGrpcTransport
@ImportGrpcClients(types = FlightServiceGrpc.FlightServiceBlockingStub.class)
class FlightGrpcFailureSimulationTest {

	@Autowired
	FlightServiceGrpc.FlightServiceBlockingStub stub;

	@MockitoBean
	FlightService flightService;

	@Test
	void firstTwoGetPriceCallsFailWithUnavailableAndThirdSucceeds() {
		when(flightService.calculatePrice(any(), any())).thenReturn(new BigDecimal("100"));
		GetPriceRequest request = GetPriceRequest.newBuilder()
				.setFlightId(UUID.randomUUID().toString())
				.setTicketClass(TicketClass.TICKET_CLASS_ECONOMY)
				.build();

		for (int attempt = 1; attempt <= 2; attempt++) {
			assertThatThrownBy(() -> stub.getPrice(request))
					.isInstanceOfSatisfying(StatusRuntimeException.class,
							e -> assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.UNAVAILABLE));
		}
		assertThat(stub.getPrice(request).getPrice().getUnits()).isEqualTo(100);
		verify(flightService, times(1)).calculatePrice(any(), any());
	}
}
