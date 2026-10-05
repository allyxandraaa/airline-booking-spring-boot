package com.example.overbooking_service.compensation;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

@SpringBootTest
@ActiveProfiles("test")
abstract class WireMockIntegrationTest {

	static final String BATCH_PATH = "/api/v1/bookings/batch";
	static final String REFUND_PATH = "/api/v1/payments/refund";
	static final BigDecimal AMOUNT = new BigDecimal("120.50");
	static final String REFUND_OK = """
			{"paymentId":"%s","bookingId":"%s","amount":120.50,"type":"REFUND","status":"SUCCESS"}
			""".formatted(UUID.randomUUID(), UUID.randomUUID());

	@RegisterExtension
	static final WireMockExtension wireMock = WireMockExtension.newInstance()
			.options(wireMockConfig().dynamicPort())
			.configureStaticDsl(true)
			.build();

	@Autowired
	CircuitBreakerRegistry circuitBreakerRegistry;

	@Autowired
	RefundGateway refundGateway;

	@DynamicPropertySource
	static void dependencyUrls(DynamicPropertyRegistry registry) {
		registry.add("booking-service.url", wireMock::baseUrl);
		registry.add("payment-service.url", wireMock::baseUrl);
	}

	@BeforeEach
	void resetState() {
		WireMock.reset();
		circuitBreakerRegistry.circuitBreaker("paymentClient").reset();
	}
}
