package com.example.booking_service.booking;

import java.util.List;
import java.util.UUID;

public interface BookingService {

	BookingDto create(CreateBookingRequest request);

	BookingDto findById(UUID id);

	List<BookingDto> findByIds(List<UUID> ids);
}
