package com.example.customer_service.passenger.internal;

import com.example.customer_service.exception.PassengerNotFoundException;
import com.example.customer_service.passenger.CreatePassengerRequest;
import com.example.customer_service.passenger.PassengerDto;
import com.example.customer_service.passenger.PassengerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PassengerController.class)
class PassengerControllerTest {

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	PassengerService passengerService;

	@Test
	void createReturns201WithPassenger() throws Exception {
		UUID id = UUID.randomUUID();
		UUID paymentMethodId = UUID.randomUUID();
		when(passengerService.create(any(CreatePassengerRequest.class))).thenReturn(new PassengerDto(
				id, "Ivan Petrenko", "ivan@example.com", "+380501234567", "AB123456", paymentMethodId));

		mockMvc.perform(post("/api/v1/passengers")
						.contentType(MediaType.APPLICATION_JSON)
						.content(createBody("Ivan Petrenko", "ivan@example.com")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").value(id.toString()))
				.andExpect(jsonPath("$.fullName").value("Ivan Petrenko"))
				.andExpect(jsonPath("$.savedPaymentMethodId").value(paymentMethodId.toString()));
		verify(passengerService).create(new CreatePassengerRequest(
				"Ivan Petrenko", "ivan@example.com", "+380501234567", "AB123456"));
	}

	@Test
	void createWithBlankNameReturns400ProblemDetail() throws Exception {
		mockMvc.perform(post("/api/v1/passengers")
						.contentType(MediaType.APPLICATION_JSON)
						.content(createBody(" ", "ivan@example.com")))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
		verifyNoInteractions(passengerService);
	}

	@Test
	void createWithInvalidEmailReturns400() throws Exception {
		mockMvc.perform(post("/api/v1/passengers")
						.contentType(MediaType.APPLICATION_JSON)
						.content(createBody("Ivan Petrenko", "not-an-email")))
				.andExpect(status().isBadRequest());
		verifyNoInteractions(passengerService);
	}

	@Test
	void createWithMissingFieldsReturns400() throws Exception {
		mockMvc.perform(post("/api/v1/passengers")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isBadRequest());
		verifyNoInteractions(passengerService);
	}

	@Test
	void getPaymentMethodReturnsId() throws Exception {
		UUID id = UUID.randomUUID();
		UUID paymentMethodId = UUID.randomUUID();
		when(passengerService.getPaymentMethodId(id)).thenReturn(paymentMethodId);

		mockMvc.perform(get("/api/v1/passengers/{id}/payment-method", id))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.paymentMethodId").value(paymentMethodId.toString()));
	}

	@Test
	void getPaymentMethodReturns404ProblemDetailWhenMissing() throws Exception {
		UUID id = UUID.randomUUID();
		when(passengerService.getPaymentMethodId(id)).thenThrow(new PassengerNotFoundException(id));

		mockMvc.perform(get("/api/v1/passengers/{id}/payment-method", id))
				.andExpect(status().isNotFound())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(404));
	}

	@Test
	void getPaymentMethodWithMalformedIdReturns400() throws Exception {
		mockMvc.perform(get("/api/v1/passengers/{id}/payment-method", "not-a-uuid"))
				.andExpect(status().isBadRequest());
		verifyNoInteractions(passengerService);
	}

	private String createBody(String fullName, String email) {
		return "{\"fullName\":\"" + fullName + "\",\"email\":\"" + email
				+ "\",\"phone\":\"+380501234567\",\"documentNumber\":\"AB123456\"}";
	}
}
