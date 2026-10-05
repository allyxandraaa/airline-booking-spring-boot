package com.example.overbooking_service.compensation.internal;

import com.example.overbooking_service.compensation.CompensationRequest;
import com.example.overbooking_service.compensation.CompensationResult;
import com.example.overbooking_service.compensation.CompensationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/overbooking")
class CompensationController {

	private final CompensationService compensationService;

	CompensationController(CompensationService compensationService) {
		this.compensationService = compensationService;
	}

	@PostMapping("/compensations")
	public List<CompensationResult> compensate(@Valid @RequestBody CompensationRequest request) {
		return compensationService.compensate(request.bookingIds());
	}
}
