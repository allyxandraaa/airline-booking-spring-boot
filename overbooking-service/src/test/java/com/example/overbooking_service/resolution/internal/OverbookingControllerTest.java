package com.example.overbooking_service.resolution.internal;

import com.example.overbooking_service.client.dto.TicketClass;
import com.example.overbooking_service.exception.FlightNotFoundException;
import com.example.overbooking_service.exception.ReportNotFoundException;
import com.example.overbooking_service.resolution.BumpOutcome;
import com.example.overbooking_service.resolution.BumpedBookingDto;
import com.example.overbooking_service.resolution.OverbookingReportDto;
import com.example.overbooking_service.resolution.OverbookingResolutionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OverbookingController.class)
class OverbookingControllerTest {

	private static final UUID FLIGHT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
	private static final UUID REPORT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
	private static final UUID BOOKING_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	OverbookingResolutionService resolutionService;

	@Test
	void resolutionCreatesReportForFlight() throws Exception {
		when(resolutionService.resolve(FLIGHT_ID)).thenReturn(report());

		mockMvc.perform(post("/api/v1/overbooking/flights/{flightId}/resolution", FLIGHT_ID))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").value(REPORT_ID.toString()))
				.andExpect(jsonPath("$.flightId").value(FLIGHT_ID.toString()))
				.andExpect(jsonPath("$.bumpedCount").value(1))
				.andExpect(jsonPath("$.totalCompensation").value(120.50))
				.andExpect(jsonPath("$.bumpedBookings[0].bookingId").value(BOOKING_ID.toString()))
				.andExpect(jsonPath("$.bumpedBookings[0].ticketClass").value("ECONOMY"))
				.andExpect(jsonPath("$.bumpedBookings[0].outcome").value("COMPENSATED"));
	}

	@Test
	void resolutionOfUnknownFlightReturnsProblemDetail() throws Exception {
		when(resolutionService.resolve(FLIGHT_ID)).thenThrow(new FlightNotFoundException(FLIGHT_ID));

		mockMvc.perform(post("/api/v1/overbooking/flights/{flightId}/resolution", FLIGHT_ID))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.title").value("Не знайдено"))
				.andExpect(jsonPath("$.timestamp").exists());
	}

	@Test
	void malformedFlightIdIsBadRequest() throws Exception {
		mockMvc.perform(post("/api/v1/overbooking/flights/{flightId}/resolution", "not-a-uuid"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void reportIsReturnedById() throws Exception {
		when(resolutionService.findReport(REPORT_ID)).thenReturn(report());

		mockMvc.perform(get("/api/v1/overbooking/reports/{reportId}", REPORT_ID))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(REPORT_ID.toString()))
				.andExpect(jsonPath("$.seatsEconomy").value(150));
	}

	@Test
	void unknownReportReturnsNotFound() throws Exception {
		when(resolutionService.findReport(REPORT_ID)).thenThrow(new ReportNotFoundException(REPORT_ID));

		mockMvc.perform(get("/api/v1/overbooking/reports/{reportId}", REPORT_ID))
				.andExpect(status().isNotFound());
	}

	private static OverbookingReportDto report() {
		return new OverbookingReportDto(REPORT_ID, FLIGHT_ID, 150, 12, 1, new BigDecimal("120.50"),
				Instant.parse("2026-11-21T12:00:00Z"),
				List.of(new BumpedBookingDto(BOOKING_ID, TicketClass.ECONOMY, new BigDecimal("120.50"),
						BumpOutcome.COMPENSATED)));
	}
}
