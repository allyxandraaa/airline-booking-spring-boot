package com.example.overbooking_service.client;

import com.example.overbooking_service.client.dto.BatchRequest;
import com.example.overbooking_service.client.dto.BookingDto;
import com.example.overbooking_service.client.dto.RefundRequest;
import com.example.overbooking_service.client.dto.RefundResponse;
import com.github.tomakehurst.wiremock.WireMockServer;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.ResourceAccessException;
import org.wiremock.spring.ConfigureWireMock;
import org.wiremock.spring.EnableWireMock;
import org.wiremock.spring.InjectWireMock;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@EnableWireMock({
		@ConfigureWireMock(name = "booking-wm", baseUrlProperties = "booking-service.url"),
		@ConfigureWireMock(name = "payment-wm", baseUrlProperties = "payment-service.url")
})
class ClientConfigTest {

	@Autowired
	BookingClient bookingClient;

	@Autowired
	PaymentClient paymentClient;

	@Autowired
	CircuitBreakerRegistry circuitBreakerRegistry;

	@InjectWireMock("booking-wm")
	WireMockServer bookingWireMock;

	@InjectWireMock("payment-wm")
	WireMockServer paymentWireMock;

	@AfterEach
	void clearMdc() {
		MDC.clear();
	}

	@Test
	void contextProvidesClientsAndResilienceRegistry() {
		assertThat(bookingClient).isNotNull();
		assertThat(paymentClient).isNotNull();
		assertThat(circuitBreakerRegistry).isNotNull();
	}

	@Test
	void bookingClientIgnoresUnknownFieldsAndSendsCorrelationId() {
		UUID id = UUID.randomUUID();
		bookingWireMock.stubFor(post(urlPathEqualTo("/api/v1/bookings/batch"))
				.willReturn(aResponse()
						.withHeader("Content-Type", "application/json")
						.withBody("""
								[{"id":"%s","priceAtBooking":120.50,"status":"CONFIRMED","unknownField":"x"}]
								""".formatted(id))));
		MDC.put("correlationId", "test-id");

		List<BookingDto> result = bookingClient.getBookingsByIds(new BatchRequest(List.of(id)));

		assertThat(result).hasSize(1);
		assertThat(result.get(0).id()).isEqualTo(id);
		assertThat(result.get(0).priceAtBooking()).isEqualByComparingTo("120.50");
		bookingWireMock.verify(postRequestedFor(urlPathEqualTo("/api/v1/bookings/batch"))
				.withHeader("X-Correlation-Id", equalTo("test-id")));
	}

	@Test
	void paymentClientSendsIdempotencyKeyAndCorrelationId() {
		UUID bookingId = UUID.randomUUID();
		paymentWireMock.stubFor(post(urlPathEqualTo("/api/v1/payments/refund"))
				.willReturn(aResponse()
						.withHeader("Content-Type", "application/json")
						.withBody("""
								{"paymentId":"%s","bookingId":"%s","amount":120.50,"type":"REFUND","status":"SUCCESS"}
								""".formatted(UUID.randomUUID(), bookingId))));
		MDC.put("correlationId", "test-id");

		RefundResponse response = paymentClient.refund("key-1",
				new RefundRequest(bookingId, new BigDecimal("120.50")));

		assertThat(response.status()).isEqualTo("SUCCESS");
		paymentWireMock.verify(postRequestedFor(urlPathEqualTo("/api/v1/payments/refund"))
				.withHeader("Idempotency-Key", equalTo("key-1"))
				.withHeader("X-Correlation-Id", equalTo("test-id")));
	}

	@Test
	void readTimeoutThrowsResourceAccessException() {
		bookingWireMock.stubFor(post(urlPathEqualTo("/api/v1/bookings/batch"))
				.willReturn(aResponse()
						.withHeader("Content-Type", "application/json")
						.withBody("[]")
						.withFixedDelay(3500)));

		assertThatThrownBy(() -> bookingClient.getBookingsByIds(new BatchRequest(List.of(UUID.randomUUID()))))
				.isInstanceOf(ResourceAccessException.class);
	}
}
