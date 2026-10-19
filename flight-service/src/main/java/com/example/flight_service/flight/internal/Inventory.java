package com.example.flight_service.flight.internal;

import com.example.flight_service.flight.TicketClass;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Entity
@Table(name = "inventories")
class Inventory {

	@Id
	private UUID flightId;

	@Column(nullable = false)
	private int totalSeatsEconomy;

	@Column(nullable = false)
	private int totalSeatsBusiness;

	@Column(nullable = false)
	private int seatsSoldEconomy;

	@Column(nullable = false)
	private int seatsSoldBusiness;

	@Column(nullable = false)
	private int overbookingLimitPercent;

	protected Inventory() {
	}

	Inventory(UUID flightId, int totalSeatsEconomy, int totalSeatsBusiness, int overbookingLimitPercent) {
		this.flightId = flightId;
		this.totalSeatsEconomy = totalSeatsEconomy;
		this.totalSeatsBusiness = totalSeatsBusiness;
		this.overbookingLimitPercent = overbookingLimitPercent;
	}

	int sold(TicketClass ticketClass) {
		return ticketClass == TicketClass.ECONOMY ? seatsSoldEconomy : seatsSoldBusiness;
	}

	BigDecimal occupancy(TicketClass ticketClass) {
		int total = totalSeats(ticketClass);
		return BigDecimal.valueOf(Math.min(sold(ticketClass), total))
				.divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP);
	}

	boolean reserve(TicketClass ticketClass) {
		int total = totalSeats(ticketClass);
		int capacity = total + total * overbookingLimitPercent / 100;
		if (sold(ticketClass) >= capacity) {
			return false;
		}
		if (ticketClass == TicketClass.ECONOMY) {
			seatsSoldEconomy++;
		} else {
			seatsSoldBusiness++;
		}
		return true;
	}

	void release(TicketClass ticketClass) {
		if (ticketClass == TicketClass.ECONOMY) {
			seatsSoldEconomy--;
		} else {
			seatsSoldBusiness--;
		}
	}

	private int totalSeats(TicketClass ticketClass) {
		return ticketClass == TicketClass.ECONOMY ? totalSeatsEconomy : totalSeatsBusiness;
	}

	public int getTotalSeatsEconomy() {
		return totalSeatsEconomy;
	}

	public int getTotalSeatsBusiness() {
		return totalSeatsBusiness;
	}

	public int getOverbookingLimitPercent() {
		return overbookingLimitPercent;
	}
}
