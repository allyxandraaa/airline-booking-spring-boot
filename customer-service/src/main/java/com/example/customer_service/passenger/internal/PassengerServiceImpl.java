package com.example.customer_service.passenger.internal;

import com.example.customer_service.exception.PassengerNotFoundException;
import com.example.customer_service.passenger.CreatePassengerRequest;
import com.example.customer_service.passenger.PassengerDto;
import com.example.customer_service.passenger.PassengerService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

@Service
class PassengerServiceImpl implements PassengerService {

	private final PassengerRepository passengerRepository;
	private final TransactionTemplate transactionTemplate;

	PassengerServiceImpl(PassengerRepository passengerRepository, PlatformTransactionManager transactionManager) {
		this.passengerRepository = passengerRepository;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
	}

	@Override
	public PassengerDto create(CreatePassengerRequest request) {
		return transactionTemplate.execute(status -> toDto(passengerRepository.save(new Passenger(
				request.fullName(), request.email(), request.phone(), request.documentNumber(),
				UUID.randomUUID()))));
	}

	@Override
	@Transactional(readOnly = true)
	public UUID getPaymentMethodId(UUID passengerId) {
		return passengerRepository.findById(passengerId)
				.map(Passenger::getSavedPaymentMethodId)
				.orElseThrow(() -> new PassengerNotFoundException(passengerId));
	}

	private PassengerDto toDto(Passenger passenger) {
		return new PassengerDto(
				passenger.getId(),
				passenger.getFullName(),
				passenger.getEmail(),
				passenger.getPhone(),
				passenger.getDocumentNumber(),
				passenger.getSavedPaymentMethodId());
	}
}
