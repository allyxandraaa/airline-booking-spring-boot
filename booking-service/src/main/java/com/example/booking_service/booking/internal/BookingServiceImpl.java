package com.example.booking_service.booking.internal;

import com.example.booking_service.booking.BookingDto;
import com.example.booking_service.booking.BookingReservedEvent;
import com.example.booking_service.booking.BookingService;
import com.example.booking_service.booking.CreateBookingRequest;
import com.example.booking_service.exception.BookingNotFoundException;
import com.example.booking_service.exception.BookingProcessingException;
import com.example.booking_service.integration.FlightGateway;
import com.example.booking_service.integration.PassengerGateway;
import com.example.booking_service.integration.SeatUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
class BookingServiceImpl implements BookingService {

	private static final Logger log = LoggerFactory.getLogger(BookingServiceImpl.class);

	private final BookingRepository bookingRepository;
	private final OutboxRepository outboxRepository;
	private final FlightGateway flightGateway;
	private final PassengerGateway passengerGateway;
	private final TransactionTemplate transactionTemplate;
	private final JsonMapper jsonMapper;

	BookingServiceImpl(BookingRepository bookingRepository,
					   OutboxRepository outboxRepository,
					   FlightGateway flightGateway,
					   PassengerGateway passengerGateway,
					   PlatformTransactionManager transactionManager,
					   JsonMapper jsonMapper) {
		this.bookingRepository = bookingRepository;
		this.outboxRepository = outboxRepository;
		this.flightGateway = flightGateway;
		this.passengerGateway = passengerGateway;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
		this.jsonMapper = jsonMapper;
	}

	@Override
	public BookingDto create(CreateBookingRequest request) {
		UUID bookingId = transactionTemplate.execute(status -> bookingRepository.save(new Booking(
				request.flightId(), request.passengerId(), request.passengerName(),
				request.ticketClass(), Instant.now())).getId());

		BigDecimal price;
		UUID reservationId;
		try {
			price = flightGateway.getPrice(request.flightId(), request.ticketClass());
			reservationId = flightGateway.reserveSeat(request.flightId(), request.ticketClass());
		} catch (SeatUnavailableException e) {
			markFailed(bookingId);
			throw e;
		} catch (RuntimeException e) {
			markFailed(bookingId);
			throw new BookingProcessingException("Сервіс рейсів недоступний", e);
		}

		try {
			UUID paymentMethodId = passengerGateway.getPaymentMethodId(request.passengerId());
			return transactionTemplate.execute(status ->
					reserveAndEnqueuePayment(bookingId, price, reservationId, paymentMethodId));
		} catch (RuntimeException e) {
			compensate(bookingId, reservationId);
			throw new BookingProcessingException("Не вдалося завершити бронювання", e);
		}
	}

	@Override
	@Transactional(readOnly = true)
	public BookingDto findById(UUID id) {
		return bookingRepository.findById(id)
				.map(this::toDto)
				.orElseThrow(() -> new BookingNotFoundException(id));
	}

	@Override
	@Transactional(readOnly = true)
	public List<BookingDto> findByIds(List<UUID> ids) {
		return bookingRepository.findAllById(ids).stream()
				.map(this::toDto)
				.toList();
	}

	private BookingDto reserveAndEnqueuePayment(UUID bookingId, BigDecimal price, UUID reservationId,
												UUID paymentMethodId) {
		Booking booking = bookingRepository.findById(bookingId)
				.orElseThrow(() -> new BookingNotFoundException(bookingId));
		booking.reserve(price, reservationId);
		bookingRepository.save(booking);

		BookingReservedEvent event = BookingReservedEvent.now(bookingId, paymentMethodId, price);
		outboxRepository.save(new OutboxRecord(event.eventId(), "Booking", bookingId, "BookingReserved",
				jsonMapper.writeValueAsString(event), Instant.now()));
		return toDto(booking);
	}

	private void compensate(UUID bookingId, UUID reservationId) {
		try {
			flightGateway.releaseSeat(reservationId);
		} catch (RuntimeException e) {
			log.error("Не вдалося звільнити місце {} для бронювання {}", reservationId, bookingId, e);
		}
		markFailed(bookingId);
	}

	private void markFailed(UUID bookingId) {
		try {
			transactionTemplate.executeWithoutResult(status -> bookingRepository.findById(bookingId)
					.ifPresent(booking -> {
						booking.fail();
						bookingRepository.save(booking);
					}));
		} catch (RuntimeException e) {
			log.error("Не вдалося позначити бронювання {} як FAILED", bookingId, e);
		}
	}

	private BookingDto toDto(Booking booking) {
		return new BookingDto(
				booking.getId(),
				booking.getFlightId(),
				booking.getPassengerId(),
				booking.getPassengerName(),
				booking.getTicketClass(),
				booking.getStatus(),
				booking.getPriceAtBooking(),
				booking.getReservationId(),
				booking.getPurchaseTime());
	}
}
