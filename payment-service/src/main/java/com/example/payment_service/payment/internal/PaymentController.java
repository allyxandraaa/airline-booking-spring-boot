package com.example.payment_service.payment.internal;

import com.example.payment_service.payment.PaymentService;
import com.example.payment_service.payment.RefundRequest;
import com.example.payment_service.payment.RefundResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
class PaymentController {

	private final PaymentService paymentService;

	PaymentController(PaymentService paymentService) {
		this.paymentService = paymentService;
	}

	@PostMapping("/refund")
	public RefundResponse refund(@RequestHeader("Idempotency-Key") @NotBlank String idempotencyKey,
								 @Valid @RequestBody RefundRequest request) {
		return paymentService.refund(idempotencyKey, request);
	}
}
