package com.example.flight_service.flight.internal;

import com.example.flight_service.exception.FlightNotFoundException;
import com.example.flight_service.exception.ReservationNotFoundException;
import com.example.flight_service.exception.SeatUnavailableException;
import com.example.flight_service.flight.CreateFlightRequest;
import com.example.flight_service.flight.FlightDto;
import com.example.flight_service.flight.FlightService;
import com.example.flight_service.flight.ReservationDto;
import com.example.flight_service.flight.TicketClass;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FlightController.class)
class FlightControllerTest {

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	FlightService flightService;

	@Test
	void createReturns201WithFlight() throws Exception {
		UUID id = UUID.randomUUID();
		Instant departure = Instant.now().plusSeconds(86400 * 40);
		when(flightService.create(any(CreateFlightRequest.class))).thenReturn(new FlightDto(
				id, "PS101", "KBP", "WAW", departure, 150, 12,
				new BigDecimal("120.00"), new BigDecimal("250.00"), 10));

		mockMvc.perform(post("/api/v1/flights")
						.contentType(MediaType.APPLICATION_JSON)
						.content(createBody("PS101", departure, 150)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").value(id.toString()))
				.andExpect(jsonPath("$.flightNumber").value("PS101"))
				.andExpect(jsonPath("$.totalSeatsEconomy").value(150))
				.andExpect(jsonPath("$.basePriceBusiness").value(250.00))
				.andExpect(jsonPath("$.overbookingLimitPercent").value(10));
		verify(flightService).create(new CreateFlightRequest(
				"PS101", "KBP", "WAW", departure, 150, 12,
				new BigDecimal("120.00"), new BigDecimal("250.00"), 10));
	}

	@Test
	void createWithPastDepartureReturns400() throws Exception {
		mockMvc.perform(post("/api/v1/flights")
						.contentType(MediaType.APPLICATION_JSON)
						.content(createBody("PS101", Instant.now().minusSeconds(3600), 150)))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
		verifyNoInteractions(flightService);
	}

	@Test
	void createWithNonPositiveSeatsReturns400() throws Exception {
		mockMvc.perform(post("/api/v1/flights")
						.contentType(MediaType.APPLICATION_JSON)
						.content(createBody("PS101", Instant.now().plusSeconds(86400), 0)))
				.andExpect(status().isBadRequest());
		verifyNoInteractions(flightService);
	}

	@Test
	void createWithMissingFieldsReturns400() throws Exception {
		mockMvc.perform(post("/api/v1/flights")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isBadRequest());
		verifyNoInteractions(flightService);
	}

	@Test
	void getPriceReturnsCalculatedPrice() throws Exception {
		UUID id = UUID.randomUUID();
		when(flightService.calculatePrice(id, TicketClass.BUSINESS)).thenReturn(new BigDecimal("312.50"));

		mockMvc.perform(get("/api/v1/flights/{id}/price", id).param("ticketClass", "BUSINESS"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.price").value(312.50));
	}

	@Test
	void getPriceForUnknownFlightReturns404ProblemDetail() throws Exception {
		UUID id = UUID.randomUUID();
		when(flightService.calculatePrice(id, TicketClass.ECONOMY)).thenThrow(new FlightNotFoundException(id));

		mockMvc.perform(get("/api/v1/flights/{id}/price", id).param("ticketClass", "ECONOMY"))
				.andExpect(status().isNotFound())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(404));
	}

	@Test
	void getPriceWithoutTicketClassReturns400() throws Exception {
		mockMvc.perform(get("/api/v1/flights/{id}/price", UUID.randomUUID()))
				.andExpect(status().isBadRequest());
		verifyNoInteractions(flightService);
	}

	@Test
	void getPriceWithUnknownTicketClassReturns400() throws Exception {
		mockMvc.perform(get("/api/v1/flights/{id}/price", UUID.randomUUID()).param("ticketClass", "FIRST"))
				.andExpect(status().isBadRequest());
		verifyNoInteractions(flightService);
	}

	@Test
	void reserveSeatReturns201WithReservationId() throws Exception {
		UUID flightId = UUID.randomUUID();
		UUID reservationId = UUID.randomUUID();
		when(flightService.reserveSeat(flightId, TicketClass.ECONOMY)).thenReturn(new ReservationDto(reservationId));

		mockMvc.perform(post("/api/v1/flights/{id}/reservations", flightId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"ticketClass\":\"ECONOMY\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.reservationId").value(reservationId.toString()));
	}

	@Test
	void reserveSeatWithoutFreeSeatsReturns409ProblemDetail() throws Exception {
		UUID flightId = UUID.randomUUID();
		when(flightService.reserveSeat(flightId, TicketClass.BUSINESS))
				.thenThrow(new SeatUnavailableException(flightId, TicketClass.BUSINESS));

		mockMvc.perform(post("/api/v1/flights/{id}/reservations", flightId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"ticketClass\":\"BUSINESS\"}"))
				.andExpect(status().isConflict())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(409));
	}

	@Test
	void reserveSeatWithoutTicketClassReturns400() throws Exception {
		mockMvc.perform(post("/api/v1/flights/{id}/reservations", UUID.randomUUID())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isBadRequest());
		verifyNoInteractions(flightService);
	}

	@Test
	void releaseSeatReturns204() throws Exception {
		UUID reservationId = UUID.randomUUID();

		mockMvc.perform(delete("/api/v1/reservations/{id}", reservationId))
				.andExpect(status().isNoContent());
		verify(flightService).releaseSeat(reservationId);
	}

	@Test
	void releaseUnknownReservationReturns404ProblemDetail() throws Exception {
		UUID reservationId = UUID.randomUUID();
		doThrow(new ReservationNotFoundException(reservationId)).when(flightService).releaseSeat(reservationId);

		mockMvc.perform(delete("/api/v1/reservations/{id}", reservationId))
				.andExpect(status().isNotFound())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(404));
	}

	private String createBody(String flightNumber, Instant departure, int economySeats) {
		return "{\"flightNumber\":\"" + flightNumber + "\",\"origin\":\"KBP\",\"destination\":\"WAW\","
				+ "\"departureTime\":\"" + departure + "\",\"totalSeatsEconomy\":" + economySeats
				+ ",\"totalSeatsBusiness\":12,\"basePriceEconomy\":120.00,\"basePriceBusiness\":250.00,"
				+ "\"overbookingLimitPercent\":10}";
	}
}
