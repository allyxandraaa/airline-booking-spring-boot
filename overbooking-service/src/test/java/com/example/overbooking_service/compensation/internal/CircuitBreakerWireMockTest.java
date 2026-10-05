package com.example.overbooking_service.compensation.internal;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = "resilience4j.retry.instances.paymentClient.max-attempts=1")
class CircuitBreakerWireMockTest extends WireMockIntegrationTest {

	@Test
	void errorStormOpensCircuitAndStopsCallingPaymentService() {
		stubFor(post(urlPathEqualTo(REFUND_PATH)).willReturn(serverError()));
		UUID bookingId = UUID.randomUUID();

		for (int call = 1; call <= 10; call++) {
			refundGateway.refund(bookingId, AMOUNT, "key-" + call);
		}

		assertThat(circuitBreakerRegistry.circuitBreaker("paymentClient").getState())
				.isEqualTo(CircuitBreaker.State.OPEN);
		assertThat(refundGateway.refund(bookingId, AMOUNT, "key-11").status()).isEqualTo("PENDING");
		verify(10, postRequestedFor(urlPathEqualTo(REFUND_PATH)));
	}
}
