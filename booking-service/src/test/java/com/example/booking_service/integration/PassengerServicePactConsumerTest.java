package com.example.booking_service.integration;

import au.com.dius.pact.consumer.MockServer;
import au.com.dius.pact.consumer.dsl.PactDslWithProvider;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.core.model.PactSpecVersion;
import au.com.dius.pact.core.model.V4Pact;
import au.com.dius.pact.core.model.annotations.Pact;
import au.com.dius.pact.core.model.annotations.PactDirectory;
import com.example.booking_service.config.ClientConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.UUID;

import static au.com.dius.pact.consumer.dsl.LambdaDsl.newJsonBody;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "passenger-service", pactVersion = PactSpecVersion.V4)
@PactDirectory("../contracts/pacts")
class PassengerServicePactConsumerTest {

	private static final String PASSENGER_ID = "3b7c1d5e-9f20-4c6a-b8d1-62e0a4f95c37";
	private static final String PAYMENT_METHOD_ID = "a41e6f08-5d93-4b27-8c15-7f3d0e92b6a4";
	private static final String JSON = "application/json";

	@Pact(consumer = "booking-service", provider = "passenger-service")
	V4Pact paymentMethodOfExistingPassenger(PactDslWithProvider builder) {
		return builder
				.given("passenger exists", Map.of("passengerId", PASSENGER_ID, "paymentMethodId", PAYMENT_METHOD_ID))
				.uponReceiving("a payment method request for an existing passenger")
				.method("GET")
				.path("/api/v1/passengers/" + PASSENGER_ID + "/payment-method")
				.willRespondWith()
				.status(200)
				.headers(Map.of("Content-Type", JSON))
				.body(newJsonBody(body -> body.uuid("paymentMethodId", UUID.fromString(PAYMENT_METHOD_ID))).build())
				.toPact(V4Pact.class);
	}

	@Pact(consumer = "booking-service", provider = "passenger-service")
	V4Pact paymentMethodOfUnknownPassenger(PactDslWithProvider builder) {
		return builder
				.given("passenger does not exist", Map.of("passengerId", PASSENGER_ID))
				.uponReceiving("a payment method request for an unknown passenger")
				.method("GET")
				.path("/api/v1/passengers/" + PASSENGER_ID + "/payment-method")
				.willRespondWith()
				.status(404)
				.toPact(V4Pact.class);
	}

	@Test
	@PactTestFor(pactMethod = "paymentMethodOfExistingPassenger")
	void gatewayReadsPaymentMethodFromPassengerService(MockServer mockServer) {
		UUID paymentMethodId = gateway(mockServer).getPaymentMethodId(UUID.fromString(PASSENGER_ID));

		assertThat(paymentMethodId).isEqualTo(UUID.fromString(PAYMENT_METHOD_ID));
	}

	@Test
	@PactTestFor(pactMethod = "paymentMethodOfUnknownPassenger")
	void gatewayMapsNotFoundToPassengerNotFound(MockServer mockServer) {
		PassengerGatewayImpl gateway = gateway(mockServer);
		UUID passengerId = UUID.fromString(PASSENGER_ID);

		assertThatThrownBy(() -> gateway.getPaymentMethodId(passengerId)).isInstanceOf(PassengerNotFoundException.class);
	}

	private PassengerGatewayImpl gateway(MockServer mockServer) {
		return new PassengerGatewayImpl(new ClientConfig().passengerClient(RestClient.builder(), mockServer.getUrl()));
	}
}
