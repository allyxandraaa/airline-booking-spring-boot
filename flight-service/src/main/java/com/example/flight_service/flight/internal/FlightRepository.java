package com.example.flight_service.flight.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface FlightRepository extends JpaRepository<Flight, UUID> {
}
