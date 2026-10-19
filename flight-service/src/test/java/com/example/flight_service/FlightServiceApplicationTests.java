package com.example.flight_service;

import com.example.flight_service.flight.FlightService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
class FlightServiceApplicationTests {

	@MockitoBean
	FlightService flightService;

	@Test
	void contextLoads() {
	}

}
