package com.example.booking_service.booking;

import java.util.List;
import java.util.UUID;

public interface BookingService {

	List<BookingDto> findByIds(List<UUID> ids);
}
