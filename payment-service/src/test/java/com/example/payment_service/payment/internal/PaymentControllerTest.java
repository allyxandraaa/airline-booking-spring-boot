package com.example.payment_service.payment.internal;

import com.example.payment_service.payment.PaymentDto;
import com.example.payment_service.payment.PaymentService;
import com.example.payment_service.payment.PaymentStatus;
import com.example.payment_service.payment.PaymentType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PaymentController.class)
class PaymentControllerTest {

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	PaymentService paymentService;

	@Test
	void paymentsByBookingIdReturns200WithList() throws Exception {
		UUID bookingId = UUID.randomUUID();
		UUID paymentId = UUID.randomUUID();
		UUID paymentMethodId = UUID.randomUUID();
		when(paymentService.findByBookingId(bookingId)).thenReturn(List.of(new PaymentDto(
				paymentId, bookingId, paymentMethodId, new BigDecimal("120.50"), PaymentType.CHARGE, PaymentStatus.SUCCESS)));

		mockMvc.perform(get("/api/v1/payments").param("bookingId", bookingId.toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].paymentId").value(paymentId.toString()))
				.andExpect(jsonPath("$[0].paymentMethodId").value(paymentMethodId.toString()))
				.andExpect(jsonPath("$[0].type").value("CHARGE"))
				.andExpect(jsonPath("$[0].status").value("SUCCESS"));
	}

	@Test
	void invalidOrMissingBookingIdReturns400ProblemDetail() throws Exception {
		mockMvc.perform(get("/api/v1/payments").param("bookingId", "not-a-uuid"))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
		mockMvc.perform(get("/api/v1/payments"))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
		verifyNoInteractions(paymentService);
	}
}
