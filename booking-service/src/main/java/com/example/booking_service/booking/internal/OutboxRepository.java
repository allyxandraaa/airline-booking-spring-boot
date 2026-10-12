package com.example.booking_service.booking.internal;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.QueryHints;

import java.util.List;
import java.util.UUID;

interface OutboxRepository extends JpaRepository<OutboxRecord, UUID> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
	List<OutboxRecord> findTop50ByProcessedFalseOrderByCreatedAtAsc();
}
