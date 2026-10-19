package com.example.booking_service.integration.client;

import com.example.booking_service.booking.TicketClass;
import com.example.booking_service.integration.client.dto.PriceResponse;
import com.example.booking_service.integration.client.dto.ReservationResponse;
import com.example.booking_service.integration.client.dto.ReserveSeatRequest;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.DeleteExchange;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.PostExchange;

import java.util.UUID;

public interface FlightClient {

	@GetExchange("/api/v1/flights/{id}/price")
	PriceResponse getPrice(@PathVariable UUID id, @RequestParam TicketClass ticketClass);

	@PostExchange("/api/v1/flights/{id}/reservations")
	ReservationResponse reserveSeat(@PathVariable UUID id, @RequestBody ReserveSeatRequest request);

	@DeleteExchange("/api/v1/reservations/{id}")
	void releaseSeat(@PathVariable UUID id);
}
