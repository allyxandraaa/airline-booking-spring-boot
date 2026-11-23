package com.example.payment_service.payment.internal;

import com.example.payment_service.payment.PaymentService;
import com.example.payment_service.payment.PaymentDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
class PaymentController {

	private final PaymentService paymentService;

	PaymentController(PaymentService paymentService) {
		this.paymentService = paymentService;
	}

	@GetMapping
	public List<PaymentDto> findByBookingId(@RequestParam UUID bookingId) {
		return paymentService.findByBookingId(bookingId);
	}
}
