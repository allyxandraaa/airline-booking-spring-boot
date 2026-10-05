package com.example.overbooking_service.compensation.internal;

import com.example.overbooking_service.compensation.CompensationResult;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@TestPropertySource(properties = "resilience4j.retry.instances.paymentClient.wait-duration=50ms")
class CompensationWireMockTest extends WireMockIntegrationTest {

	@Autowired
	MockMvc mockMvc;

	@Test
	void refundIsRetriedWithSameIdempotencyKeyUntilSuccess() {
		stubFor(post(urlPathEqualTo(REFUND_PATH)).inScenario("retry")
				.whenScenarioStateIs(Scenario.STARTED).willReturn(serverError()).willSetStateTo("second"));
		stubFor(post(urlPathEqualTo(REFUND_PATH)).inScenario("retry")
				.whenScenarioStateIs("second").willReturn(serverError()).willSetStateTo("third"));
		stubFor(post(urlPathEqualTo(REFUND_PATH)).inScenario("retry")
				.whenScenarioStateIs("third").willReturn(okJson(REFUND_OK)));

		CompensationResult result = refundGateway.refund(UUID.randomUUID(), AMOUNT, "key-1");

		assertThat(result.status()).isEqualTo("COMPLETED");
		verify(3, postRequestedFor(urlPathEqualTo(REFUND_PATH)));
		verify(3, postRequestedFor(urlPathEqualTo(REFUND_PATH)).withHeader("Idempotency-Key", equalTo("key-1")));
	}

	@Test
	void bookingServiceFailureReturns503ProblemDetail() throws Exception {
		stubFor(post(urlPathEqualTo(BATCH_PATH)).willReturn(serverError()));

		mockMvc.perform(compensations(UUID.randomUUID()))
				.andExpect(status().isServiceUnavailable())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(503))
				.andExpect(jsonPath("$.title").isNotEmpty())
				.andExpect(jsonPath("$.detail").isNotEmpty())
				.andExpect(jsonPath("$.timestamp").exists());
	}

	@Test
	void correlationIdReachesBothServicesAndResponse() throws Exception {
		UUID id = UUID.randomUUID();
		stubBookings(id);
		stubFor(post(urlPathEqualTo(REFUND_PATH)).willReturn(okJson(REFUND_OK)));

		mockMvc.perform(compensations(id).header("X-Correlation-Id", "test-id"))
				.andExpect(status().isOk())
				.andExpect(header().string("X-Correlation-Id", "test-id"));

		verify(postRequestedFor(urlPathEqualTo(BATCH_PATH)).withHeader("X-Correlation-Id", equalTo("test-id")));
		verify(postRequestedFor(urlPathEqualTo(REFUND_PATH)).withHeader("X-Correlation-Id", equalTo("test-id")));
	}

	@Test
	void threeBookingsUseOneBatchRequestAndThreeRefunds() throws Exception {
		UUID[] ids = {UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()};
		stubBookings(ids);
		stubFor(post(urlPathEqualTo(REFUND_PATH)).willReturn(okJson(REFUND_OK)));

		mockMvc.perform(compensations(ids))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(3));

		verify(1, postRequestedFor(urlPathEqualTo(BATCH_PATH)));
		verify(3, postRequestedFor(urlPathEqualTo(REFUND_PATH)));
	}

	private void stubBookings(UUID... ids) {
		String body = Arrays.stream(ids)
				.map(id -> "{\"id\":\"%s\",\"priceAtBooking\":120.50,\"status\":\"BUMPED\"}".formatted(id))
				.collect(Collectors.joining(",", "[", "]"));
		stubFor(post(urlPathEqualTo(BATCH_PATH)).willReturn(okJson(body)));
	}

	private MockHttpServletRequestBuilder compensations(UUID... ids) {
		String body = Arrays.stream(ids).map(id -> "\"" + id + "\"")
				.collect(Collectors.joining(",", "{\"bookingIds\":[", "]}"));
		return MockMvcRequestBuilders.post("/api/v1/overbooking/compensations")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body);
	}
}
