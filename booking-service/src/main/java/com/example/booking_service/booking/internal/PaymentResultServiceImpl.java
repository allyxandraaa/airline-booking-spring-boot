package com.example.booking_service.booking.internal;

import com.example.booking_service.booking.BookingStatus;
import com.example.booking_service.booking.PaymentProcessedEvent;
import com.example.booking_service.booking.PaymentProcessedStatus;
import com.example.booking_service.booking.PaymentResultService;
import com.example.booking_service.exception.TransientBookingException;
import com.example.booking_service.integration.FlightGateway;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;

@Service
class PaymentResultServiceImpl implements PaymentResultService {

	private static final Logger log = LoggerFactory.getLogger(PaymentResultServiceImpl.class);

	private final BookingRepository bookingRepository;
	private final ProcessedEventRepository processedEventRepository;
	private final FlightGateway flightGateway;
	private final TransactionTemplate transactionTemplate;
	private final Timer confirmationTimer;

	PaymentResultServiceImpl(BookingRepository bookingRepository,
							 ProcessedEventRepository processedEventRepository,
							 FlightGateway flightGateway,
							 PlatformTransactionManager transactionManager,
							 MeterRegistry registry) {
		this.bookingRepository = bookingRepository;
		this.processedEventRepository = processedEventRepository;
		this.flightGateway = flightGateway;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
		this.confirmationTimer = Timer.builder("bookings.confirmation.duration")
				.description("Час від створення бронювання до підтвердження оплати")
				.register(registry);
	}

	@Override
	public void applyPaymentResult(PaymentProcessedEvent event) {
		if (processedEventRepository.existsById(event.eventId())) {
			log.info("Подію {} уже оброблено: дублікат, пропускаємо", event.eventId());
			return;
		}
		Booking booking = bookingRepository.findById(event.bookingId()).orElse(null);
		if (booking == null) {
			log.warn("Бронювання {} для події {} не знайдено, пропускаємо", event.bookingId(), event.eventId());
			return;
		}
		if (booking.getStatus() != BookingStatus.SEAT_RESERVED) {
			log.info("Бронювання {} уже має статус {}, подію {} ігноруємо", booking.getId(), booking.getStatus(), event.eventId());
			return;
		}
		if (event.status() != PaymentProcessedStatus.SUCCESS) {
			releaseSeat(booking);
		}
		try {
			transactionTemplate.executeWithoutResult(status -> apply(event));
		} catch (DataIntegrityViolationException e) {
			log.info("Подію {} оброблено паралельно: дублікат, пропускаємо", event.eventId());
		}
	}

	private void apply(PaymentProcessedEvent event) {
		Booking booking = bookingRepository.findById(event.bookingId()).orElseThrow();
		boolean changed = event.status() == PaymentProcessedStatus.SUCCESS
				? booking.confirm(event.paymentId())
				: booking.failPayment(event.paymentId());
		if (!changed) {
			log.info("Бронювання {} уже має статус {}, подію {} ігноруємо", booking.getId(), booking.getStatus(), event.eventId());
			return;
		}
		bookingRepository.save(booking);
		processedEventRepository.saveAndFlush(new ProcessedEvent(event.eventId(), Instant.now()));
		if (booking.getStatus() == BookingStatus.CONFIRMED) {
			confirmationTimer.record(Duration.between(booking.getPurchaseTime(), Instant.now()));
		}
		log.info("Бронювання {} тепер має статус {} після результату оплати {} (причина: {})",
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
