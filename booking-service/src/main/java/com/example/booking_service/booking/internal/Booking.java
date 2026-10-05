package com.example.booking_service.booking.internal;

import com.example.booking_service.booking.BookingStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "bookings")
class Booking {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false)
	private UUID flightId;

	@Column(nullable = false)
	private UUID passengerId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private BookingStatus status;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal priceAtBooking;

	@Column(nullable = false)
	private Instant purchaseTime;

	protected Booking() {
	}

	Booking(UUID flightId, UUID passengerId, BookingStatus status,
				   BigDecimal priceAtBooking, Instant purchaseTime) {
		this.flightId = flightId;
		this.passengerId = passengerId;
		this.status = status;
		this.priceAtBooking = priceAtBooking;
		this.purchaseTime = purchaseTime;
	}

	public UUID getId() {
		return id;
	}

	public UUID getFlightId() {
		return flightId;
	}

	public UUID getPassengerId() {
		return passengerId;
	}

	public BookingStatus getStatus() {
		return status;
	}

	public BigDecimal getPriceAtBooking() {
		return priceAtBooking;
	}

	public Instant getPurchaseTime() {
		return purchaseTime;
	}
}
