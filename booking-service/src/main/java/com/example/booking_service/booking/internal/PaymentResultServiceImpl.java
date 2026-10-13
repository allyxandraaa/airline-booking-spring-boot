package com.example.booking_service.booking.internal;

import com.example.booking_service.booking.BookingStatus;
import com.example.booking_service.booking.PaymentProcessedEvent;
import com.example.booking_service.booking.PaymentProcessedStatus;
import com.example.booking_service.booking.PaymentResultService;
import com.example.booking_service.exception.TransientBookingException;
import com.example.booking_service.integration.FlightGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;

@Service
class PaymentResultServiceImpl implements PaymentResultService {

	private static final Logger log = LoggerFactory.getLogger(PaymentResultServiceImpl.class);

	private final BookingRepository bookingRepository;
	private final ProcessedEventRepository processedEventRepository;
	private final FlightGateway flightGateway;
	private final TransactionTemplate transactionTemplate;

	PaymentResultServiceImpl(BookingRepository bookingRepository,
							 ProcessedEventRepository processedEventRepository,
							 FlightGateway flightGateway,
							 PlatformTransactionManager transactionManager) {
		this.bookingRepository = bookingRepository;
		this.processedEventRepository = processedEventRepository;
		this.flightGateway = flightGateway;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
	}

	@Override
	public void applyPaymentResult(PaymentProcessedEvent event) {
		if (processedEventRepository.existsById(event.eventId())) {
			log.info("Event {} already processed: duplicate, skip", event.eventId());
			return;
		}
		Booking booking = bookingRepository.findById(event.bookingId()).orElse(null);
		if (booking == null) {
			log.warn("Booking {} not found for event {}, skipping", event.bookingId(), event.eventId());
			return;
		}
		if (booking.getStatus() != BookingStatus.SEAT_RESERVED) {
			log.info("Booking {} is already {}, ignoring event {}", booking.getId(), booking.getStatus(), event.eventId());
			return;
		}
		if (event.status() != PaymentProcessedStatus.SUCCESS) {
			releaseSeat(booking);
		}
		try {
			transactionTemplate.executeWithoutResult(status -> apply(event));
		} catch (DataIntegrityViolationException e) {
			log.info("Event {} processed concurrently: duplicate, skip", event.eventId());
		}
	}

	private void apply(PaymentProcessedEvent event) {
		Booking booking = bookingRepository.findById(event.bookingId()).orElseThrow();
		boolean changed = event.status() == PaymentProcessedStatus.SUCCESS
				? booking.confirm(event.paymentId())
				: booking.failPayment(event.paymentId());
		if (!changed) {
			log.info("Booking {} is already {}, ignoring event {}", booking.getId(), booking.getStatus(), event.eventId());
			return;
		}
		bookingRepository.save(booking);
		processedEventRepository.saveAndFlush(new ProcessedEvent(event.eventId(), Instant.now()));
		log.info("Booking {} is now {} after payment result {} (reason: {})",
				booking.getId(), booking.getStatus(), event.status(), event.reason());
	}

	private void releaseSeat(Booking booking) {
		try {
			flightGateway.releaseSeat(booking.getReservationId());
		} catch (RuntimeException e) {
			throw new TransientBookingException("Не вдалося звільнити місце для бронювання " + booking.getId(), e);
		}
	}
}
