package com.example.passenger_service.passenger.internal;

import com.example.passenger_service.exception.PassengerNotFoundException;
import com.example.passenger_service.passenger.CreatePassengerRequest;
import com.example.passenger_service.passenger.PassengerDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = "DB_PASSWORD=unused")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PassengerPostgresTest {

	@ServiceConnection
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

	static {
		POSTGRES.start();
	}

	@Autowired
	PassengerRepository passengerRepository;

	@Autowired
	PlatformTransactionManager transactionManager;

	private PassengerServiceImpl service;

	@BeforeEach
	void setUp() {
		service = new PassengerServiceImpl(passengerRepository, transactionManager);
	}

	@AfterEach
	void cleanDatabase() {
		passengerRepository.deleteAll();
	}

	@Test
	void createdPassengerIsStoredAndItsPaymentMethodCanBeRead() {
		PassengerDto created = service.create(request("ivan@example.com"));

		assertThat(passengerRepository.count()).isEqualTo(1);
		assertThat(created.id()).isNotNull();
		assertThat(service.getPaymentMethodId(created.id())).isEqualTo(created.savedPaymentMethodId());
	}

	@Test
	void everyPassengerGetsItsOwnPaymentMethod() {
		PassengerDto first = service.create(request("first@example.com"));
		PassengerDto second = service.create(request("second@example.com"));

		assertThat(first.savedPaymentMethodId()).isNotEqualTo(second.savedPaymentMethodId());
		assertThat(passengerRepository.count()).isEqualTo(2);
	}

	@Test
	void unknownPassengerIsNotFound() {
		UUID unknownId = UUID.randomUUID();

		assertThatThrownBy(() -> service.getPaymentMethodId(unknownId)).isInstanceOf(PassengerNotFoundException.class);
	}

	private static CreatePassengerRequest request(String email) {
		return new CreatePassengerRequest("Ivan Petrenko", email, "+380501234567", "AB123456");
	}
}
