package com.example.booking_service;

import com.example.booking_service.booking.BookingService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
class BookingServiceApplicationTests {

	@MockitoBean
	BookingService bookingService;

	@Test
	void contextLoads() {
	}

}
