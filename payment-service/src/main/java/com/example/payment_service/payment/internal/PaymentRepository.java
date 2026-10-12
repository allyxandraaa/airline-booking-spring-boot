package com.example.payment_service.payment.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface PaymentRepository extends JpaRepository<Payment, UUID> {

	List<Payment> findByBookingId(UUID bookingId);
}
