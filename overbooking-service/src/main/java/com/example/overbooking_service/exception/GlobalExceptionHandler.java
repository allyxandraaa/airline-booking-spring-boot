package com.example.overbooking_service.exception;

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
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

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

	@ExceptionHandler({ResourceAccessException.class, HttpServerErrorException.class,
			CallNotPermittedException.class, BulkheadFullException.class})
	public ProblemDetail handleDependencyUnavailable(Exception ex) {
		log.warn("Dependency unavailable: {}", ex.toString());
		return problem(HttpStatus.SERVICE_UNAVAILABLE, "Сервіс недоступний",
				"Необхідний зовнішній сервіс тимчасово недоступний");
	}

	@ExceptionHandler(HttpClientErrorException.class)
	public ProblemDetail handleDependencyRejected(HttpClientErrorException ex) {
		log.warn("Dependency rejected the request: {}", ex.toString());
		return problem(HttpStatus.BAD_GATEWAY, "Помилка шлюзу",
				"Зовнішній сервіс відхилив запит");
	}

	@ExceptionHandler(BookingsNotFoundException.class)
	public ProblemDetail handleBookingsNotFound(BookingsNotFoundException ex) {
		return problem(HttpStatus.NOT_FOUND, "Не знайдено", ex.getMessage());
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
