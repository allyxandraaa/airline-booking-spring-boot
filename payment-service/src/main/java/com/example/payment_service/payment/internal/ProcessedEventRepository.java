package com.example.payment_service.payment.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, UUID> {
}
