package com.example.overbooking_service.exception;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/test")
class ThrowingController {

	record Body(@NotBlank String name) {
	}

	@PostMapping("/validation")
	void validation(@Valid @RequestBody Body body) {
	}

	@GetMapping("/header")
	void header(@RequestHeader("X-Required") String value) {
	}

	@GetMapping("/boom")
	void boom() {
		throw new IllegalStateException("secret internal detail");
	}

	@GetMapping("/timeout")
	void timeout() {
		throw new ResourceAccessException("read timed out");
	}

	@GetMapping("/server-error")
	void serverError() {
		throw new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR);
	}

	@GetMapping("/circuit-open")
	void circuitOpen() {
		throw CallNotPermittedException.createCallNotPermittedException(CircuitBreaker.ofDefaults("test"));
	}

	@GetMapping("/bulkhead-full")
	void bulkheadFull() {
		throw BulkheadFullException.createBulkheadFullException(Bulkhead.ofDefaults("test"));
	}

	@GetMapping("/client-error")
	void clientError() {
		throw new HttpClientErrorException(HttpStatus.NOT_FOUND);
	}
}
