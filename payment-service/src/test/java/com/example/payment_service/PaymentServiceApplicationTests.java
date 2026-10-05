package com.example.payment_service;

import com.example.payment_service.payment.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
class PaymentServiceApplicationTests {

	@MockitoBean
	PaymentService paymentService;

	@Test
	void contextLoads() {
	}

}
