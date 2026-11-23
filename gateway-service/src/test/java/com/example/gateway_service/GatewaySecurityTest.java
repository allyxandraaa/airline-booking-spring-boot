package com.example.gateway_service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.context.ApplicationContext;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
		"PASSENGER_SERVICE_URL=http://localhost:1",
		"FLIGHT_SERVICE_URL=http://localhost:1",
		"BOOKING_SERVICE_URL=http://localhost:1",
		"PAYMENT_SERVICE_URL=http://localhost:1",
		"OVERBOOKING_SERVICE_URL=http://localhost:1"
})
class GatewaySecurityTest {

	private static final String FLIGHT_PRICE_URI = "/api/v1/flights/11111111-1111-1111-1111-111111111111/price?ticketClass=ECONOMY";

	@Autowired
	ApplicationContext context;

	@MockitoBean
	RedisRateLimiter rateLimiter;

	WebTestClient client;

	@BeforeEach
	void setUp() {
		client = WebTestClient.bindToApplicationContext(context)
				.apply(SecurityMockServerConfigurers.springSecurity())
				.configureClient()
				.build();
		when(rateLimiter.isAllowed(any(), any())).thenReturn(Mono.just(new RateLimiter.Response(true, Map.of())));
	}

	@Test
	void requestWithoutTokenIsUnauthorized() {
		client.get().uri(FLIGHT_PRICE_URI)
				.exchange()
				.expectStatus().isUnauthorized();
	}

	@Test
	void requestWithValidTokenIsRoutedToTheService() {
		client.mutateWith(SecurityMockServerConfigurers.mockJwt())
				.get().uri(FLIGHT_PRICE_URI)
				.exchange()
				.expectStatus().value(status -> assertThat(status).isNotIn(401, 403, 429));
	}

	@Test
	void requestOverTheLimitIsRejectedWithTooManyRequests() {
		when(rateLimiter.isAllowed(any(), any())).thenReturn(Mono.just(new RateLimiter.Response(false, Map.of())));

		client.mutateWith(SecurityMockServerConfigurers.mockJwt())
				.get().uri(FLIGHT_PRICE_URI)
				.exchange()
				.expectStatus().isEqualTo(429);
	}

	@Test
	void livenessProbeIsOpenWithoutToken() {
		client.get().uri("/actuator/health/liveness")
				.exchange()
				.expectStatus().isOk();
	}

	@Test
	void unknownPathIsRejectedBeforeRouting() {
		client.get().uri("/api/v1/unknown")
				.exchange()
				.expectStatus().isUnauthorized();
	}
}
