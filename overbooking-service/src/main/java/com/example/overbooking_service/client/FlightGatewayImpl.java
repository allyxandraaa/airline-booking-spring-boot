package com.example.overbooking_service.client;

import com.example.grpc.flight.v1.FlightServiceGrpc;
import com.example.grpc.flight.v1.GetFlightCapacityRequest;
import com.example.grpc.flight.v1.GetFlightCapacityResponse;
import com.example.overbooking_service.client.dto.FlightCapacityDto;
import com.example.overbooking_service.exception.FlightNotFoundException;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
class FlightGatewayImpl implements FlightGateway {

	private final FlightServiceGrpc.FlightServiceBlockingStub flightStub;

	FlightGatewayImpl(FlightServiceGrpc.FlightServiceBlockingStub flightStub) {
		this.flightStub = flightStub;
	}

	@Override
	public FlightCapacityDto getCapacity(UUID flightId) {
		try {
			GetFlightCapacityResponse response = flightStub.getFlightCapacity(
					GetFlightCapacityRequest.newBuilder().setFlightId(flightId.toString()).build());
			return new FlightCapacityDto(response.getSeatsEconomy(), response.getSeatsBusiness());
		} catch (StatusRuntimeException e) {
			if (e.getStatus().getCode() == Status.Code.NOT_FOUND) {
				throw new FlightNotFoundException(flightId);
			}
			throw e;
		}
	}
}
