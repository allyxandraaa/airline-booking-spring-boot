package com.example.gateway_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
@EnableWebFluxSecurity
class GatewaySecurityConfig {

	@Bean
	SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
		return http
				.authorizeExchange(exchange -> exchange
						.pathMatchers("/actuator/health/**", "/actuator/prometheus").permitAll()
						.anyExchange().authenticated())
				.oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
				.csrf(ServerHttpSecurity.CsrfSpec::disable)
				.build();
	}
}
