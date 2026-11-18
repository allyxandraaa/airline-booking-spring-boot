package com.example.overbooking_service.compensation.internal;

import com.example.overbooking_service.client.BookingClient;
import com.example.overbooking_service.client.dto.BatchRequest;
import com.example.overbooking_service.client.dto.BookingDto;
import com.example.overbooking_service.compensation.CompensationResult;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CompensationController.class)
@Import({CompensationServiceImpl.class, SimpleMeterRegistry.class})
class CompensationControllerTest {

	private static final BigDecimal AMOUNT = new BigDecimal("120.50");

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	BookingClient bookingClient;

	@MockitoBean
	RefundGateway refundGateway;

	@Test
	void threeBookingsUseOneBatchCallAndOneRefundStream() throws Exception {
		List<UUID> ids = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
		when(bookingClient.getBookingsByIds(new BatchRequest(ids))).thenReturn(ids.stream()
				.map(id -> new BookingDto(id, AMOUNT, "BUMPED"))
				.toList());
		List<RefundOrder> orders = ids.stream().map(id -> new RefundOrder(id, AMOUNT, key(id))).toList();
		when(refundGateway.refundAll(orders)).thenReturn(List.of(
				new CompensationResult(ids.get(0), AMOUNT, "COMPLETED"),
				new CompensationResult(ids.get(1), AMOUNT, "COMPLETED"),
				new CompensationResult(ids.get(2), AMOUNT, "PENDING")));

		mockMvc.perform(compensations(ids))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(3))
				.andExpect(jsonPath("$[0].bookingId").value(ids.get(0).toString()))
				.andExpect(jsonPath("$[0].amount").value(120.50))
				.andExpect(jsonPath("$[0].status").value("COMPLETED"))
				.andExpect(jsonPath("$[2].status").value("PENDING"));

		verify(bookingClient, times(1)).getBookingsByIds(any());
		verify(refundGateway, times(1)).refundAll(anyList());
	}

	@Test
	void missingBookingReturns404ProblemDetail() throws Exception {
		UUID found = UUID.randomUUID();
		UUID missing = UUID.randomUUID();
		when(bookingClient.getBookingsByIds(any()))
				.thenReturn(List.of(new BookingDto(found, new BigDecimal("80.00"), "BUMPED")));

		mockMvc.perform(compensations(List.of(found, missing)))
				.andExpect(status().isNotFound())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(404))
				.andExpect(jsonPath("$.detail").value(containsString(missing.toString())))
				.andExpect(jsonPath("$.timestamp").exists());
		verifyNoInteractions(refundGateway);
	}

	@Test
	void emptyBookingIdsReturns400() throws Exception {
		mockMvc.perform(compensations(List.of()))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
		verifyNoInteractions(bookingClient);
	}

	@Test
	void correlationIdIsEchoedOrGenerated() throws Exception {
		mockMvc.perform(compensations(List.of()).header("X-Correlation-Id", "abc"))
				.andExpect(header().string("X-Correlation-Id", "abc"));
		mockMvc.perform(compensations(List.of()))
				.andExpect(header().string("X-Correlation-Id", matchesPattern("[0-9a-f-]{36}")));
	}

	private static String key(UUID bookingId) {
		return UUID.nameUUIDFromBytes(("refund:" + bookingId).getBytes(StandardCharsets.UTF_8)).toString();
	}

	private MockHttpServletRequestBuilder compensations(List<UUID> ids) {
		String body = ids.stream().map(id -> "\"" + id + "\"")
				.collect(Collectors.joining(",", "{\"bookingIds\":[", "]}"));
		return post("/api/v1/overbooking/compensations")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body);
	}
}
