package com.example.passenger_service.passenger.internal;

import au.com.dius.pact.provider.junit5.HttpTestTarget;
import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junit5.PactVerificationInvocationContextProvider;
import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.State;
import au.com.dius.pact.provider.junitsupport.loader.PactFolder;
import com.example.passenger_service.exception.PassengerNotFoundException;
import com.example.passenger_service.passenger.PassengerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.when;

@Provider("passenger-service")
@PactFolder("../contracts/pacts")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PassengerServicePactProviderTest {

	@LocalServerPort
	int port;

	@MockitoBean
	PassengerService passengerService;

	@BeforeEach
	void setTarget(PactVerificationContext context) {
		context.setTarget(new HttpTestTarget("localhost", port));
	}

	@TestTemplate
	@ExtendWith(PactVerificationInvocationContextProvider.class)
	void verifyPact(PactVerificationContext context) {
		context.verifyInteraction();
	}

	@State("passenger exists")
	void passengerExists(Map<String, Object> params) {
		when(passengerService.getPaymentMethodId(id(params, "passengerId")))
				.thenReturn(id(params, "paymentMethodId"));
	}

	@State("passenger does not exist")
	void passengerDoesNotExist(Map<String, Object> params) {
		UUID passengerId = id(params, "passengerId");
		when(passengerService.getPaymentMethodId(passengerId)).thenThrow(new PassengerNotFoundException(passengerId));
	}

	private UUID id(Map<String, Object> params, String key) {
		return UUID.fromString((String) params.get(key));
	}
}
