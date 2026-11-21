package com.example.booking_service;

import com.example.booking_service.booking.BookingService;
import com.example.booking_service.booking.PaymentResultService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class BookingServiceApplicationTests {

	@MockitoBean
	BookingService bookingService;

	@MockitoBean
	PaymentResultService paymentResultService;

	@Autowired
	ApplicationContext context;

	@Test
	void contextLoads() {
		assertThat(context.getStartupDate()).isPositive();
	}

}
