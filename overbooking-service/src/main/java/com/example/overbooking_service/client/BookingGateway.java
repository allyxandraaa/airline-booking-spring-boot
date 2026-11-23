package com.example.overbooking_service.client;

import com.example.overbooking_service.client.dto.BatchRequest;
import com.example.overbooking_service.client.dto.BookingDto;
import com.example.overbooking_service.client.dto.ConfirmedBookingDto;

import java.util.List;
import java.util.UUID;

public interface BookingGateway {

	List<BookingDto> getBookingsByIds(BatchRequest request);

	List<ConfirmedBookingDto> getConfirmedBookings(UUID flightId);

	void bump(UUID bookingId);
}
