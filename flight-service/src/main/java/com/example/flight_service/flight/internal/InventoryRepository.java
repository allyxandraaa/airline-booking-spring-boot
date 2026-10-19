package com.example.flight_service.flight.internal;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;
import java.util.UUID;

interface InventoryRepository extends JpaRepository<Inventory, UUID> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<Inventory> findByFlightId(UUID flightId);
}
