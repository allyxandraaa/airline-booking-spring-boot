package com.example.payment_service.payment.internal;

import com.example.payment_service.payment.PaymentService;
import com.example.payment_service.payment.PaymentStatus;
import com.example.payment_service.payment.PaymentType;
import com.example.payment_service.payment.RefundRequest;
import com.example.payment_service.payment.RefundResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
	void validRequestReturns200WithRefund() throws Exception {
		UUID bookingId = UUID.randomUUID();
		UUID paymentId = UUID.randomUUID();
		String key = UUID.randomUUID().toString();
		RefundRequest request = new RefundRequest(bookingId, new BigDecimal("120.50"));
		when(paymentService.refund(key, request)).thenReturn(new RefundResponse(
				paymentId, bookingId, new BigDecimal("120.50"), PaymentType.REFUND, PaymentStatus.SUCCESS));

		mockMvc.perform(post("/api/v1/payments/refund")
						.header("Idempotency-Key", key)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"bookingId\":\"" + bookingId + "\",\"amount\":120.50}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.paymentId").value(paymentId.toString()))
				.andExpect(jsonPath("$.bookingId").value(bookingId.toString()))
				.andExpect(jsonPath("$.amount").value(120.50))
				.andExpect(jsonPath("$.type").value("REFUND"))
				.andExpect(jsonPath("$.status").value("SUCCESS"));
		verify(paymentService).refund(key, request);
	}

	@Test
	void missingIdempotencyKeyReturns400ProblemDetail() throws Exception {
		mockMvc.perform(post("/api/v1/payments/refund")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"bookingId\":\"" + UUID.randomUUID() + "\",\"amount\":10}"))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.title").value("Відсутній заголовок"))
				.andExpect(jsonPath("$.detail").value("Відсутній необхідний заголовок «Idempotency-Key»"));
		verifyNoInteractions(paymentService);
	}

	@Test
	void blankIdempotencyKeyReturns400() throws Exception {
		mockMvc.perform(post("/api/v1/payments/refund")
						.header("Idempotency-Key", " ")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"bookingId\":\"" + UUID.randomUUID() + "\",\"amount\":10}"))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
		verifyNoInteractions(paymentService);
	}

	@Test
	void nonPositiveAmountReturns400() throws Exception {
		for (String amount : new String[]{"0", "-5"}) {
			mockMvc.perform(post("/api/v1/payments/refund")
							.header("Idempotency-Key", UUID.randomUUID().toString())
							.contentType(MediaType.APPLICATION_JSON)
							.content("{\"bookingId\":\"" + UUID.randomUUID() + "\",\"amount\":" + amount + "}"))
					.andExpect(status().isBadRequest())
					.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
					.andExpect(jsonPath("$.title").value("Помилка валідації"));
		}
		verifyNoInteractions(paymentService);
	}

	@Test
	void missingBookingIdReturns400() throws Exception {
		mockMvc.perform(post("/api/v1/payments/refund")
						.header("Idempotency-Key", UUID.randomUUID().toString())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"amount\":10}"))
				.andExpect(status().isBadRequest());
		verifyNoInteractions(paymentService);
	}
}
