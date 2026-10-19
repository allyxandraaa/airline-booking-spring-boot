package com.example.booking_service.integration;

import com.example.booking_service.config.ClientConfig;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.web.client.RestClient;

import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PassengerGatewayTest {

	@RegisterExtension
	static final WireMockExtension wireMock = WireMockExtension.newInstance()
			.options(wireMockConfig().dynamicPort())
			.build();

	private final UUID passengerId = UUID.randomUUID();
	private PassengerGatewayImpl gateway;

	@BeforeEach
	void setUp() {
		gateway = new PassengerGatewayImpl(
				new ClientConfig().passengerClient(RestClient.builder(), wireMock.baseUrl()));
	}

	@Test
	void getPaymentMethodIdReturnsIdFromCustomerService() {
		UUID paymentMethodId = UUID.randomUUID();
		wireMock.stubFor(get(urlPathEqualTo("/api/v1/passengers/" + passengerId + "/payment-method"))
				.willReturn(aResponse().withHeader("Content-Type", "application/json")
						.withBody("{\"paymentMethodId\":\"" + paymentMethodId + "\"}")));

		assertThat(gateway.getPaymentMethodId(passengerId)).isEqualTo(paymentMethodId);
	}

	@Test
	void getPaymentMethodIdForUnknownPassengerThrowsPassengerNotFound() {
		wireMock.stubFor(get(urlPathEqualTo("/api/v1/passengers/" + passengerId + "/payment-method"))
				.willReturn(aResponse().withStatus(404).withHeader("Content-Type", "application/problem+json")
						.withBody("{\"status\":404}")));

		assertThatThrownBy(() -> gateway.getPaymentMethodId(passengerId))
				.isInstanceOf(PassengerNotFoundException.class);
	}
}
