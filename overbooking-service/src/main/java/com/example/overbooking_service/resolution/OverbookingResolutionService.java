package com.example.overbooking_service.resolution;

import java.util.UUID;

public interface OverbookingResolutionService {

	OverbookingReportDto resolve(UUID flightId);

	OverbookingReportDto findReport(UUID reportId);
}
