package com.example.overbooking_service.compensation;

import com.example.overbooking_service.client.PaymentClient;
import com.example.overbooking_service.client.dto.RefundRequest;
import com.example.overbooking_service.client.dto.RefundResponse;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class RefundGateway {

	private final PaymentClient paymentClient;

	public RefundGateway(PaymentClient paymentClient) {
		this.paymentClient = paymentClient;
	}

	public CompensationResult refund(UUID bookingId, BigDecimal amount, String idempotencyKey) {
		RefundResponse response = paymentClient.refund(idempotencyKey, new RefundRequest(bookingId, amount));
		String status = "SUCCESS".equals(response.status()) ? "COMPLETED" : "PENDING";
		return new CompensationResult(bookingId, amount, status);
	}
}
