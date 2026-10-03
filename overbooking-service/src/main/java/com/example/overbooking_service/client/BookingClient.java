package com.example.overbooking_service.client;

import com.example.overbooking_service.client.dto.BatchRequest;
import com.example.overbooking_service.client.dto.BookingDto;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.PostExchange;

import java.util.List;

public interface BookingClient {

	@PostExchange("/api/v1/bookings/batch")
	List<BookingDto> getBookingsByIds(@RequestBody BatchRequest request);
}
