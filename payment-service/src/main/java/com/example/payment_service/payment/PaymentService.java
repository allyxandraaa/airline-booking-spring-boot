package com.example.payment_service.payment;

public interface PaymentService {

	RefundResponse refund(String idempotencyKey, RefundRequest request);
}
