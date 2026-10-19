package com.example.flight_service.flight.internal;

import com.example.flight_service.flight.CreateFlightRequest;
import com.example.flight_service.flight.FlightDto;
import com.example.flight_service.flight.FlightService;
import com.example.flight_service.flight.PriceDto;
import com.example.flight_service.flight.ReservationDto;
import com.example.flight_service.flight.ReserveSeatRequest;
import com.example.flight_service.flight.TicketClass;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
class FlightController {

	private final FlightService flightService;

	FlightController(FlightService flightService) {
		this.flightService = flightService;
	}

	@PostMapping("/flights")
	@ResponseStatus(HttpStatus.CREATED)
	public FlightDto create(@Valid @RequestBody CreateFlightRequest request) {
		return flightService.create(request);
	}

	@GetMapping("/flights/{id}/price")
	public PriceDto getPrice(@PathVariable UUID id, @RequestParam TicketClass ticketClass) {
		return new PriceDto(flightService.calculatePrice(id, ticketClass));
	}

	@PostMapping("/flights/{id}/reservations")
	@ResponseStatus(HttpStatus.CREATED)
	public ReservationDto reserveSeat(@PathVariable UUID id, @Valid @RequestBody ReserveSeatRequest request) {
		return flightService.reserveSeat(id, request.ticketClass());
	}

	@DeleteMapping("/reservations/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void releaseSeat(@PathVariable UUID id) {
		flightService.releaseSeat(id);
	}
}
