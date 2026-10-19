package com.example.booking_service.config;

import com.example.booking_service.integration.client.FlightClient;
import com.example.booking_service.integration.client.PassengerClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class ClientConfig {

	private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
	private static final Duration READ_TIMEOUT = Duration.ofSeconds(3);

	@Bean
	public FlightClient flightClient(RestClient.Builder builder,
									 @Value("${flight-service.url}") String baseUrl) {
		return createClient(builder, baseUrl, FlightClient.class);
	}

	@Bean
	public PassengerClient passengerClient(RestClient.Builder builder,
										   @Value("${customer-service.url}") String baseUrl) {
		return createClient(builder, baseUrl, PassengerClient.class);
	}

	private <T> T createClient(RestClient.Builder builder, String baseUrl, Class<T> clientType) {
		HttpClient httpClient = HttpClient.newBuilder()
				.version(HttpClient.Version.HTTP_1_1)
				.connectTimeout(CONNECT_TIMEOUT)
				.build();
		JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
		factory.setReadTimeout(READ_TIMEOUT);
		RestClient restClient = builder
				.baseUrl(baseUrl)
				.requestFactory(factory)
				.build();
		HttpServiceProxyFactory proxyFactory = HttpServiceProxyFactory
				.builderFor(RestClientAdapter.create(restClient))
				.build();
		return proxyFactory.createClient(clientType);
	}
}
