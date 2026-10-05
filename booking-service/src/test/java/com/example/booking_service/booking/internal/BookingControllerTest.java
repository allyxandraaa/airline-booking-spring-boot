package com.example.booking_service.booking.internal;

import com.example.booking_service.booking.BookingDto;
import com.example.booking_service.booking.BookingService;
import com.example.booking_service.booking.BookingStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BookingController.class)
class BookingControllerTest {

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	BookingService bookingService;

	@Test
	void validRequestReturns200WithBookings() throws Exception {
		UUID id = UUID.randomUUID();
		UUID flightId = UUID.randomUUID();
		UUID passengerId = UUID.randomUUID();
		when(bookingService.findByIds(List.of(id))).thenReturn(List.of(new BookingDto(
				id, flightId, passengerId, BookingStatus.CONFIRMED,
				new BigDecimal("120.50"), Instant.parse("2026-10-03T10:15:30Z"))));

		mockMvc.perform(post("/api/v1/bookings/batch")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"ids\":[\"" + id + "\"]}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].id").value(id.toString()))
				.andExpect(jsonPath("$[0].flightId").value(flightId.toString()))
				.andExpect(jsonPath("$[0].passengerId").value(passengerId.toString()))
				.andExpect(jsonPath("$[0].status").value("CONFIRMED"))
				.andExpect(jsonPath("$[0].priceAtBooking").value(120.50))
				.andExpect(jsonPath("$[0].purchaseTime").exists());
		verify(bookingService).findByIds(List.of(id));
	}

	@Test
	void unknownIdsAreSimplyOmitted() throws Exception {
		when(bookingService.findByIds(anyList())).thenReturn(List.of());

		mockMvc.perform(post("/api/v1/bookings/batch")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"ids\":[\"" + UUID.randomUUID() + "\"]}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void emptyIdsReturns400ProblemDetail() throws Exception {
		mockMvc.perform(post("/api/v1/bookings/batch")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"ids\":[]}"))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.title").value("Помилка валідації"));
		verifyNoInteractions(bookingService);
	}

	@Test
	void missingIdsReturns400() throws Exception {
		mockMvc.perform(post("/api/v1/bookings/batch")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isBadRequest());
		verifyNoInteractions(bookingService);
	}

	@Test
	void nullIdElementReturns400() throws Exception {
		mockMvc.perform(post("/api/v1/bookings/batch")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"ids\":[null]}"))
				.andExpect(status().isBadRequest());
		verifyNoInteractions(bookingService);
	}
}
