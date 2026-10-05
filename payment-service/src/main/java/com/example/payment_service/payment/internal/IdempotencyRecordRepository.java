package com.example.payment_service.payment.internal;

import org.springframework.data.jpa.repository.JpaRepository;

interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, String> {
}
