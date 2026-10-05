package com.example.booking_service.booking.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface BookingRepository extends JpaRepository<Booking, UUID> {
}
