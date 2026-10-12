package com.example.payment_service.payment;

import java.util.List;
import java.util.UUID;

public interface PaymentService {

	RefundResponse refund(String idempotencyKey, RefundRequest request);

	void processBookingReserved(BookingReservedEvent event);

	void recordProcessingFailure(UUID bookingId, String reason);

	List<PaymentDto> findByBookingId(UUID bookingId);
}
