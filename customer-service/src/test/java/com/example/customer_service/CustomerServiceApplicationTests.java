package com.example.customer_service;

import com.example.customer_service.passenger.PassengerService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
class CustomerServiceApplicationTests {

	@MockitoBean
	PassengerService passengerService;

	@Test
	void contextLoads() {
	}

}
