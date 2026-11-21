package com.example.overbooking_service.resolution.internal;

import com.example.overbooking_service.resolution.OverbookingReportDto;
import com.example.overbooking_service.resolution.OverbookingResolutionService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/overbooking")
class OverbookingController {

	private final OverbookingResolutionService resolutionService;

	OverbookingController(OverbookingResolutionService resolutionService) {
		this.resolutionService = resolutionService;
	}

	@PostMapping("/flights/{flightId}/resolution")
	@ResponseStatus(HttpStatus.CREATED)
	public OverbookingReportDto resolve(@PathVariable UUID flightId) {
		return resolutionService.resolve(flightId);
	}

	@GetMapping("/reports/{reportId}")
	public OverbookingReportDto getReport(@PathVariable UUID reportId) {
		return resolutionService.findReport(reportId);
	}
}
