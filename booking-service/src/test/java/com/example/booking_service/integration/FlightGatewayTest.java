package com.example.booking_service.integration;

import com.example.booking_service.booking.TicketClass;
import com.example.booking_service.config.ClientConfig;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FlightGatewayTest {

	@RegisterExtension
	static final WireMockExtension wireMock = WireMockExtension.newInstance()
			.options(wireMockConfig().dynamicPort())
			.build();

	private final UUID flightId = UUID.randomUUID();
	private FlightGatewayImpl gateway;

	@BeforeEach
	void setUp() {
		gateway = new FlightGatewayImpl(new ClientConfig().flightClient(RestClient.builder(), wireMock.baseUrl()));
	}

	@Test
	void getPriceReturnsPriceFromFlightService() {
		wireMock.stubFor(get(urlPathEqualTo("/api/v1/flights/" + flightId + "/price"))
				.withQueryParam("ticketClass", equalTo("BUSINESS"))
				.willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("{\"price\":312.50}")));

		assertThat(gateway.getPrice(flightId, TicketClass.BUSINESS)).isEqualByComparingTo(new BigDecimal("312.50"));
	}

	@Test
	void getPriceForUnknownFlightThrowsFlightNotFound() {
		wireMock.stubFor(get(urlPathEqualTo("/api/v1/flights/" + flightId + "/price"))
				.willReturn(aResponse().withStatus(404).withHeader("Content-Type", "application/problem+json")
						.withBody("{\"status\":404}")));

		assertThatThrownBy(() -> gateway.getPrice(flightId, TicketClass.ECONOMY))
				.isInstanceOf(FlightNotFoundException.class);
	}

	@Test
	void reserveSeatSendsTicketClassAndReturnsReservationId() {
		UUID reservationId = UUID.randomUUID();
		wireMock.stubFor(post(urlPathEqualTo("/api/v1/flights/" + flightId + "/reservations"))
				.willReturn(aResponse().withStatus(201).withHeader("Content-Type", "application/json")
						.withBody("{\"reservationId\":\"" + reservationId + "\"}")));

		UUID result = gateway.reserveSeat(flightId, TicketClass.BUSINESS);

		assertThat(result).isEqualTo(reservationId);
		wireMock.verify(postRequestedFor(urlPathEqualTo("/api/v1/flights/" + flightId + "/reservations"))
				.withRequestBody(equalToJson("{\"ticketClass\":\"BUSINESS\"}")));
	}

	@Test
	void reserveSeatConflictThrowsSeatUnavailable() {
		wireMock.stubFor(post(urlPathEqualTo("/api/v1/flights/" + flightId + "/reservations"))
				.willReturn(aResponse().withStatus(409).withHeader("Content-Type", "application/problem+json")
						.withBody("{\"status\":409}")));

		assertThatThrownBy(() -> gateway.reserveSeat(flightId, TicketClass.ECONOMY))
				.isInstanceOf(SeatUnavailableException.class);
	}

	@Test
	void reserveSeatForUnknownFlightThrowsFlightNotFound() {
		wireMock.stubFor(post(urlPathEqualTo("/api/v1/flights/" + flightId + "/reservations"))
				.willReturn(aResponse().withStatus(404).withHeader("Content-Type", "application/problem+json")
						.withBody("{\"status\":404}")));

		assertThatThrownBy(() -> gateway.reserveSeat(flightId, TicketClass.ECONOMY))
				.isInstanceOf(FlightNotFoundException.class);
	}

	@Test
	void reserveSeatServerErrorIsNotMasked() {
		wireMock.stubFor(post(urlPathEqualTo("/api/v1/flights/" + flightId + "/reservations"))
				.willReturn(aResponse().withStatus(503)));

		assertThatThrownBy(() -> gateway.reserveSeat(flightId, TicketClass.ECONOMY))
				.isInstanceOf(HttpServerErrorException.class);
	}

	@Test
	void releaseSeatCallsFlightService() {
		UUID reservationId = UUID.randomUUID();
		wireMock.stubFor(delete(urlPathEqualTo("/api/v1/reservations/" + reservationId))
				.willReturn(aResponse().withStatus(204)));

		gateway.releaseSeat(reservationId);

		wireMock.verify(deleteRequestedFor(urlPathEqualTo("/api/v1/reservations/" + reservationId)));
	}
}
