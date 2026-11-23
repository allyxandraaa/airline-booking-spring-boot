package com.example.passenger_service;

import com.example.passenger_service.passenger.PassengerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class PassengerServiceApplicationTests {

	@MockitoBean
	PassengerService passengerService;

	@Autowired
	ApplicationContext context;

	@Test
	void contextLoads() {
		assertThat(context.getStartupDate()).isPositive();
	}

}
