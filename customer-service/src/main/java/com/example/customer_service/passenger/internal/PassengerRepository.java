package com.example.customer_service.passenger.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface PassengerRepository extends JpaRepository<Passenger, UUID> {
}
