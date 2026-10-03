package com.example.overbooking_service.config;

import org.slf4j.MDC;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

public class CorrelationIdInterceptor implements ClientHttpRequestInterceptor {

	public static final String HEADER = "X-Correlation-Id";
	public static final String MDC_KEY = "correlationId";

	@Override
	public ClientHttpResponse intercept(HttpRequest request, byte[] body,
										ClientHttpRequestExecution execution) throws IOException {
		String correlationId = MDC.get(MDC_KEY);
		if (correlationId != null) {
			request.getHeaders().add(HEADER, correlationId);
		}
		return execution.execute(request, body);
	}
}
