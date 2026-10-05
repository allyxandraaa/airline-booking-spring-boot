package com.example.booking_service.booking.internal;

import com.example.booking_service.booking.BatchRequest;
import com.example.booking_service.booking.BookingDto;
import com.example.booking_service.booking.BookingService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/bookings")
class BookingController {

	private final BookingService bookingService;

	BookingController(BookingService bookingService) {
		this.bookingService = bookingService;
	}

	@PostMapping("/batch")
	public List<BookingDto> getBatch(@Valid @RequestBody BatchRequest request) {
		return bookingService.findByIds(request.ids());
	}
}
