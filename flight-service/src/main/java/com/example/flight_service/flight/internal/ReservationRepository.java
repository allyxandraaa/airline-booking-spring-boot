package com.example.flight_service.flight.internal;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;
import java.util.UUID;

interface ReservationRepository extends JpaRepository<Reservation, UUID> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<Reservation> findWithLockById(UUID id);
}
