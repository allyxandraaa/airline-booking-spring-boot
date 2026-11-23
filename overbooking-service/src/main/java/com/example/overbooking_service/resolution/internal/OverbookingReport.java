package com.example.overbooking_service.resolution.internal;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "overbooking_reports")
class OverbookingReport {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false)
	private UUID flightId;

	@Column(nullable = false)
	private int seatsEconomy;

	@Column(nullable = false)
	private int seatsBusiness;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal totalCompensation;

	@Column(nullable = false)
	private Instant createdAt;

	@OneToMany(cascade = CascadeType.ALL)
	@JoinColumn(name = "report_id", nullable = false)
	private List<BumpedBooking> bumpedBookings = new ArrayList<>();

	protected OverbookingReport() {
	}

	OverbookingReport(UUID flightId, int seatsEconomy, int seatsBusiness, BigDecimal totalCompensation,
					  Instant createdAt, List<BumpedBooking> bumpedBookings) {
		this.flightId = flightId;
		this.seatsEconomy = seatsEconomy;
		this.seatsBusiness = seatsBusiness;
		this.totalCompensation = totalCompensation;
		this.createdAt = createdAt;
		this.bumpedBookings.addAll(bumpedBookings);
	}

	public UUID getId() {
		return id;
	}

	public UUID getFlightId() {
		return flightId;
	}

	public int getSeatsEconomy() {
		return seatsEconomy;
	}

	public int getSeatsBusiness() {
		return seatsBusiness;
	}

	public BigDecimal getTotalCompensation() {
		return totalCompensation;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public List<BumpedBooking> getBumpedBookings() {
		return bumpedBookings;
	}
}
