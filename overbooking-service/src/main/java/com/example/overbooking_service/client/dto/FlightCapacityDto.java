package com.example.overbooking_service.client.dto;

public record FlightCapacityDto(int seatsEconomy, int seatsBusiness) {

	public int seats(TicketClass ticketClass) {
		return ticketClass == TicketClass.ECONOMY ? seatsEconomy : seatsBusiness;
	}
}
