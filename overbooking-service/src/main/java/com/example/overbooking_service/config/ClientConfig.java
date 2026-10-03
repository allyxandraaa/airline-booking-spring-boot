package com.example.overbooking_service.config;

import com.example.overbooking_service.client.BookingClient;
import com.example.overbooking_service.client.PaymentClient;
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
	public BookingClient bookingClient(RestClient.Builder builder,
									   @Value("${booking-service.url}") String baseUrl) {
		return createClient(builder, baseUrl, BookingClient.class);
	}

	@Bean
	public PaymentClient paymentClient(RestClient.Builder builder,
									   @Value("${payment-service.url}") String baseUrl) {
		return createClient(builder, baseUrl, PaymentClient.class);
	}

	private <T> T createClient(RestClient.Builder builder, String baseUrl, Class<T> clientType) {
		HttpClient httpClient = HttpClient.newBuilder()
				.version(HttpClient.Version.HTTP_2)
				.connectTimeout(CONNECT_TIMEOUT)
				.build();
		JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
		factory.setReadTimeout(READ_TIMEOUT);

		RestClient restClient = builder
				.baseUrl(baseUrl)
				.requestFactory(factory)
				.requestInterceptor(new CorrelationIdInterceptor())
				.build();

		HttpServiceProxyFactory proxyFactory = HttpServiceProxyFactory
				.builderFor(RestClientAdapter.create(restClient))
				.build();
		return proxyFactory.createClient(clientType);
	}
}
