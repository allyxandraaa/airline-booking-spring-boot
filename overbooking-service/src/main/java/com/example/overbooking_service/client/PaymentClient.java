package com.example.overbooking_service.client;

import com.example.overbooking_service.client.dto.RefundRequest;
import com.example.overbooking_service.client.dto.RefundResponse;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.PostExchange;

public interface PaymentClient {

	@PostExchange("/api/v1/payments/refund")
	RefundResponse refund(@RequestHeader("Idempotency-Key") String idempotencyKey,
						  @RequestBody RefundRequest request);
}
