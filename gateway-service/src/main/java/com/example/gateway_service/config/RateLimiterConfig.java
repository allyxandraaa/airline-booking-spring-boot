package com.example.gateway_service.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.Principal;

@Configuration
class RateLimiterConfig {

	private static final String ANONYMOUS_KEY = "anonymous";

	@Bean
	KeyResolver userKeyResolver() {
		return exchange -> exchange.getPrincipal()
				.map(Principal::getName)
				.defaultIfEmpty(ANONYMOUS_KEY);
	}
}
