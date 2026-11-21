package com.example.overbooking_service.exception;

import java.util.UUID;

public class ReportNotFoundException extends RuntimeException {

	public ReportNotFoundException(UUID id) {
		super("Звіт про овербукінг не знайдено: " + id);
	}
}
