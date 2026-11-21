package com.example.booking_service.booking.internal;

import com.example.booking_service.booking.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface BookingRepository extends JpaRepository<Booking, UUID> {

	List<Booking> findByFlightIdAndStatus(UUID flightId, BookingStatus status);
}
