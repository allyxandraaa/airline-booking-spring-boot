package com.example.overbooking_service.exception;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class BookingsNotFoundException extends RuntimeException {

	public BookingsNotFoundException(List<UUID> missingIds) {
		super("Бронювання не знайдено: "
				+ missingIds.stream().map(UUID::toString).collect(Collectors.joining(", ")));
	}
}
