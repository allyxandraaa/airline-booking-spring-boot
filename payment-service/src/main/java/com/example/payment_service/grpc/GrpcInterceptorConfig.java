package com.example.payment_service.grpc;

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
			@Value("${payment.grpc.failures-before-success:0}") int failuresBeforeSuccess) {
		return new FailureSimulatingInterceptor(failuresBeforeSuccess);
	}
}
