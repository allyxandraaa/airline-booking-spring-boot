package com.example.customer_service.passenger.internal;

import com.example.customer_service.exception.PassengerNotFoundException;
import com.example.customer_service.passenger.CreatePassengerRequest;
import com.example.customer_service.passenger.PassengerDto;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PassengerServiceTest {

	private final PassengerRepository repository = mock(PassengerRepository.class);
	private final PassengerServiceImpl service = new PassengerServiceImpl(
			repository, mock(PlatformTransactionManager.class));

	@Test
	void createStoresPassengerWithGeneratedPaymentMethod() {
		UUID id = UUID.randomUUID();
		when(repository.save(any(Passenger.class))).thenAnswer(invocation -> {
			Passenger passenger = invocation.getArgument(0);
			ReflectionTestUtils.setField(passenger, "id", id);
			return passenger;
		});

		PassengerDto dto = service.create(new CreatePassengerRequest(
				"Ivan Petrenko", "ivan@example.com", "+380501234567", "AB123456"));

		assertThat(dto.id()).isEqualTo(id);
		assertThat(dto.fullName()).isEqualTo("Ivan Petrenko");
		assertThat(dto.email()).isEqualTo("ivan@example.com");
		assertThat(dto.documentNumber()).isEqualTo("AB123456");
		assertThat(dto.savedPaymentMethodId()).isNotNull();
	}

	@Test
	void getPaymentMethodIdReturnsSavedPaymentMethod() {
		UUID id = UUID.randomUUID();
		UUID paymentMethodId = UUID.randomUUID();
		when(repository.findById(id)).thenReturn(Optional.of(new Passenger(
				"Ivan Petrenko", "ivan@example.com", "+380501234567", "AB123456", paymentMethodId)));

		assertThat(service.getPaymentMethodId(id)).isEqualTo(paymentMethodId);
	}

	@Test
	void getPaymentMethodIdForUnknownPassengerThrows() {
		UUID id = UUID.randomUUID();
		when(repository.findById(id)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.getPaymentMethodId(id))
				.isInstanceOf(PassengerNotFoundException.class);
	}
}
