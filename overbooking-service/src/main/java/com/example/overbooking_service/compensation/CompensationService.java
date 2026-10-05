package com.example.overbooking_service.compensation;

import java.util.List;
import java.util.UUID;

public interface CompensationService {

	List<CompensationResult> compensate(List<UUID> bookingIds);
}
