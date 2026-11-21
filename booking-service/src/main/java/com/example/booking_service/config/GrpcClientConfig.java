package com.example.booking_service.config;

import com.example.grpc.flight.v1.FlightServiceGrpc;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.client.ImportGrpcClients;

@Configuration
@ImportGrpcClients(target = "flight", types = FlightServiceGrpc.FlightServiceBlockingStub.class)
class GrpcClientConfig {
}
