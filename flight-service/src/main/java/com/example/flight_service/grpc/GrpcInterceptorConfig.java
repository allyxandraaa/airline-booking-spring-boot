package com.example.flight_service.grpc;

import io.grpc.ServerInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.server.GlobalServerInterceptor;

@Configuration
class GrpcInterceptorConfig {

	@Bean
	@GlobalServerInterceptor
	ServerInterceptor failureSimulatingInterceptor(
			@Value("${flight.grpc.failures-before-success:0}") int failuresBeforeSuccess) {
		return new FailureSimulatingInterceptor(failuresBeforeSuccess);
	}
}
