package com.example.booking_service.exception;

import com.example.booking_service.integration.SeatUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.Instant;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@Override
	protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
			HttpStatusCode statusCode, WebRequest request) {
		ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
		if (response != null && response.getBody() instanceof ProblemDetail problem) {
			problem.setProperty("timestamp", Instant.now());
		}
		return response;
	}

	@ExceptionHandler(BookingNotFoundException.class)
	public ProblemDetail handleBookingNotFound(BookingNotFoundException ex) {
		return problem(HttpStatus.NOT_FOUND, "Бронювання не знайдено", "Бронювання з вказаним ідентифікатором не існує");
	}

	@ExceptionHandler(SeatUnavailableException.class)
	public ProblemDetail handleSeatUnavailable(SeatUnavailableException ex) {
		return problem(HttpStatus.CONFLICT, "Місць немає", "На обраному рейсі немає вільних місць");
	}

	@ExceptionHandler(BookingProcessingException.class)
	public ProblemDetail handleBookingProcessing(BookingProcessingException ex) {
		log.error("Не вдалося обробити бронювання", ex);
		return problem(HttpStatus.SERVICE_UNAVAILABLE, "Сервіс тимчасово недоступний",
				"Не вдалося оформити бронювання, спробуйте пізніше");
	}

	@ExceptionHandler(Exception.class)
	public ProblemDetail handleUnexpected(Exception ex) {
		log.error("Unhandled exception", ex);
		return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Внутрішня помилка сервера", "Сталася непередбачена помилка");
	}

	private ProblemDetail problem(HttpStatus status, String title, String detail) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setTitle(title);
		problem.setProperty("timestamp", Instant.now());
		return problem;
	}
}
