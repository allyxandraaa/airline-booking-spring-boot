package com.example.payment_service.exception;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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
	String validation(@Valid @RequestBody Body body) {
		return body.name();
	}

	@GetMapping("/header")
	String header(@RequestHeader("X-Required") String value) {
		return value;
	}

	@GetMapping("/boom")
	void boom() {
		throw new IllegalStateException("secret internal detail");
	}
}
