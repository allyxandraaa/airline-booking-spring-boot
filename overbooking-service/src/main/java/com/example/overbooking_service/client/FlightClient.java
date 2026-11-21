package com.example.overbooking_service.client;

import com.example.overbooking_service.client.dto.FlightCapacityDto;

import java.util.UUID;

public interface FlightClient {

	FlightCapacityDto getCapacity(UUID flightId);
}
