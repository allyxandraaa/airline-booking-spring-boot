package com.example.booking_service.booking.internal;

import com.example.booking_service.booking.BookingDto;
import com.example.booking_service.booking.BookingStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BookingServiceTest {

	private final BookingRepository repository = mock(BookingRepository.class);
	private final BookingServiceImpl service = new BookingServiceImpl(repository);

	@Test
	void loadsAllBookingsWithSingleRepositoryCall() {
		List<UUID> ids = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
		List<Booking> bookings = ids.stream()
				.map(id -> new Booking(UUID.randomUUID(), UUID.randomUUID(), BookingStatus.CONFIRMED,
						new BigDecimal("100.00"), Instant.now()))
				.toList();
		when(repository.findAllById(ids)).thenReturn(bookings);

		List<BookingDto> result = service.findByIds(ids);

		assertThat(result).hasSize(3);
		assertThat(result).allSatisfy(dto -> assertThat(dto.status()).isEqualTo(BookingStatus.CONFIRMED));
		verify(repository, times(1)).findAllById(ids);
		verify(repository, never()).findById(any());
	}
}
