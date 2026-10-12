package com.example.booking_service.booking.internal;

import com.example.booking_service.booking.BatchRequest;
import com.example.booking_service.booking.BookingDto;
import com.example.booking_service.booking.BookingService;
import com.example.booking_service.booking.CreateBookingRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/bookings")
class BookingController {

	private final BookingService bookingService;

	BookingController(BookingService bookingService) {
		this.bookingService = bookingService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public BookingDto create(@Valid @RequestBody CreateBookingRequest request) {
		return bookingService.create(request);
	}

	@GetMapping("/{id}")
	public BookingDto getById(@PathVariable UUID id) {
		return bookingService.findById(id);
	}

	@PostMapping("/batch")
	public List<BookingDto> getBatch(@Valid @RequestBody BatchRequest request) {
		return bookingService.findByIds(request.ids());
	}
}
