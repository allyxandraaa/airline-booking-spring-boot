package com.example.overbooking_service.resolution.internal;

import com.example.overbooking_service.client.dto.TicketClass;
import com.example.overbooking_service.resolution.BumpOutcome;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "bumped_bookings")
class BumpedBooking {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false)
	private UUID bookingId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private TicketClass ticketClass;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal amount;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private BumpOutcome outcome;

	protected BumpedBooking() {
	}

	BumpedBooking(UUID bookingId, TicketClass ticketClass, BigDecimal amount, BumpOutcome outcome) {
		this.bookingId = bookingId;
		this.ticketClass = ticketClass;
		this.amount = amount;
		this.outcome = outcome;
	}

	public UUID getBookingId() {
		return bookingId;
	}

	public TicketClass getTicketClass() {
		return ticketClass;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public BumpOutcome getOutcome() {
		return outcome;
	}
}
