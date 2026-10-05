package com.example.overbooking_service.compensation;

import com.example.overbooking_service.client.BookingClient;
import com.example.overbooking_service.client.PaymentClient;
import com.example.overbooking_service.client.dto.BatchRequest;
import com.example.overbooking_service.client.dto.BookingDto;
import com.example.overbooking_service.client.dto.RefundRequest;
import com.example.overbooking_service.client.dto.RefundResponse;
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
import static org.mockito.ArgumentMatchers.anyString;
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
@Import({CompensationService.class, RefundGateway.class})
class CompensationControllerTest {

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	BookingClient bookingClient;

	@MockitoBean
	PaymentClient paymentClient;

	@Test
	void threeBookingsUseOneBatchCallAndThreeRefunds() throws Exception {
		List<UUID> ids = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
		when(bookingClient.getBookingsByIds(new BatchRequest(ids))).thenReturn(ids.stream()
				.map(id -> new BookingDto(id, new BigDecimal("120.50"), "BUMPED"))
				.toList());
		when(paymentClient.refund(anyString(), any())).thenAnswer(invocation -> {
			RefundRequest request = invocation.getArgument(1);
			String status = request.bookingId().equals(ids.get(2)) ? "FAILED" : "SUCCESS";
			return new RefundResponse(UUID.randomUUID(), request.bookingId(), request.amount(), status);
		});

		mockMvc.perform(compensations(ids))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(3))
				.andExpect(jsonPath("$[0].bookingId").value(ids.get(0).toString()))
				.andExpect(jsonPath("$[0].amount").value(120.50))
				.andExpect(jsonPath("$[0].status").value("COMPLETED"))
				.andExpect(jsonPath("$[2].status").value("PENDING"));

		verify(bookingClient, times(1)).getBookingsByIds(any());
		verify(paymentClient, times(3)).refund(anyString(), any());
		String key = UUID.nameUUIDFromBytes(("refund:" + ids.get(0)).getBytes(StandardCharsets.UTF_8)).toString();
		verify(paymentClient).refund(key, new RefundRequest(ids.get(0), new BigDecimal("120.50")));
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
		verifyNoInteractions(paymentClient);
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

	private MockHttpServletRequestBuilder compensations(List<UUID> ids) {
		String body = ids.stream().map(id -> "\"" + id + "\"")
				.collect(Collectors.joining(",", "{\"bookingIds\":[", "]}"));
		return post("/api/v1/overbooking/compensations")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body);
	}
}
