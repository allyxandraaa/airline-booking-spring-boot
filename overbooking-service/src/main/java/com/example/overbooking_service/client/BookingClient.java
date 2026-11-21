package com.example.overbooking_service.client;

import com.example.overbooking_service.client.dto.BatchRequest;
import com.example.overbooking_service.client.dto.BookingDto;

import java.util.List;

public interface BookingClient {

	List<BookingDto> getBookingsByIds(BatchRequest request);
}
