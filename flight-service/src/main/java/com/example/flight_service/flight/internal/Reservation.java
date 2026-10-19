package com.example.flight_service.flight.internal;

import com.example.flight_service.flight.TicketClass;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "reservations")
class Reservation {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false)
	private UUID flightId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private TicketClass ticketClass;

	@Column(nullable = false)
	private boolean released;

	protected Reservation() {
	}

	Reservation(UUID flightId, TicketClass ticketClass) {
		this.flightId = flightId;
		this.ticketClass = ticketClass;
	}

	boolean release() {
		if (released) {
			return false;
		}
		this.released = true;
		return true;
	}

	public UUID getId() {
		return id;
	}

	public UUID getFlightId() {
		return flightId;
	}

	public TicketClass getTicketClass() {
		return ticketClass;
	}
}
