package com.example.flight_service.flight.internal;

import com.example.flight_service.flight.TicketClass;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "flights")
class Flight {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false)
	private String flightNumber;

	@Column(nullable = false)
	private String origin;

	@Column(nullable = false)
	private String destination;

	@Column(nullable = false)
	private Instant departureTime;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal basePriceEconomy;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal basePriceBusiness;

	protected Flight() {
	}

	Flight(String flightNumber, String origin, String destination, Instant departureTime,
		   BigDecimal basePriceEconomy, BigDecimal basePriceBusiness) {
		this.flightNumber = flightNumber;
		this.origin = origin;
		this.destination = destination;
		this.departureTime = departureTime;
		this.basePriceEconomy = basePriceEconomy;
		this.basePriceBusiness = basePriceBusiness;
	}

	BigDecimal basePrice(TicketClass ticketClass) {
		return ticketClass == TicketClass.ECONOMY ? basePriceEconomy : basePriceBusiness;
	}

	public UUID getId() {
		return id;
	}

	public String getFlightNumber() {
		return flightNumber;
	}

	public String getOrigin() {
		return origin;
	}

	public String getDestination() {
		return destination;
	}

	public Instant getDepartureTime() {
		return departureTime;
	}

	public BigDecimal getBasePriceEconomy() {
		return basePriceEconomy;
	}

	public BigDecimal getBasePriceBusiness() {
		return basePriceBusiness;
	}
}
