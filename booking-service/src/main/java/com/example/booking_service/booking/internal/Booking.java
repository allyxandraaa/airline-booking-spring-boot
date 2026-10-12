package com.example.booking_service.booking.internal;

import com.example.booking_service.booking.BookingStatus;
import com.example.booking_service.booking.TicketClass;
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

	@Column(nullable = false)
	private String passengerName;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private TicketClass ticketClass;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private BookingStatus status;

	@Column(precision = 12, scale = 2)
	private BigDecimal priceAtBooking;

	private UUID reservationId;

	private UUID paymentId;

	@Column(nullable = false)
	private Instant purchaseTime;

	protected Booking() {
	}

	Booking(UUID flightId, UUID passengerId, String passengerName, TicketClass ticketClass, Instant purchaseTime) {
		this(flightId, passengerId, passengerName, ticketClass, BookingStatus.PENDING, null, purchaseTime);
	}

	Booking(UUID flightId, UUID passengerId, String passengerName, TicketClass ticketClass,
			BookingStatus status, BigDecimal priceAtBooking, Instant purchaseTime) {
		this.flightId = flightId;
		this.passengerId = passengerId;
		this.passengerName = passengerName;
		this.ticketClass = ticketClass;
		this.status = status;
		this.priceAtBooking = priceAtBooking;
		this.purchaseTime = purchaseTime;
	}

	void reserve(BigDecimal price, UUID reservationId) {
		if (status != BookingStatus.PENDING) {
			throw new IllegalStateException("Бронювання " + id + " не можна зарезервувати зі статусу " + status);
		}
		this.priceAtBooking = price;
		this.reservationId = reservationId;
		this.status = BookingStatus.SEAT_RESERVED;
	}

	void fail() {
		this.status = BookingStatus.FAILED;
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

	public String getPassengerName() {
		return passengerName;
	}

	public TicketClass getTicketClass() {
		return ticketClass;
	}

	public BookingStatus getStatus() {
		return status;
	}

	public BigDecimal getPriceAtBooking() {
		return priceAtBooking;
	}

	public UUID getReservationId() {
		return reservationId;
	}

	public UUID getPaymentId() {
		return paymentId;
	}

	public Instant getPurchaseTime() {
		return purchaseTime;
	}
}
