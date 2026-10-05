package com.example.booking_service.booking.internal;

import com.example.booking_service.booking.BookingDto;
import com.example.booking_service.booking.BookingService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
class BookingServiceImpl implements BookingService {

	private final BookingRepository bookingRepository;

	BookingServiceImpl(BookingRepository bookingRepository) {
		this.bookingRepository = bookingRepository;
	}

	@Override
	@Transactional(readOnly = true)
	public List<BookingDto> findByIds(List<UUID> ids) {
		return bookingRepository.findAllById(ids).stream()
				.map(this::toDto)
				.toList();
	}

	private BookingDto toDto(Booking booking) {
		return new BookingDto(
				booking.getId(),
				booking.getFlightId(),
				booking.getPassengerId(),
				booking.getStatus(),
				booking.getPriceAtBooking(),
				booking.getPurchaseTime());
	}
}
