package com.example.payment_service.payment.internal;

import com.example.payment_service.payment.PaymentStatus;
import com.example.payment_service.payment.PaymentType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "payments")
class Payment {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false)
	private UUID bookingId;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal amount;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private PaymentStatus status;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private PaymentType type;

	protected Payment() {
	}

	Payment(UUID bookingId, BigDecimal amount, PaymentStatus status, PaymentType type) {
		this.bookingId = bookingId;
		this.amount = amount;
		this.status = status;
		this.type = type;
	}

	UUID getId() {
		return id;
	}

	UUID getBookingId() {
		return bookingId;
	}

	BigDecimal getAmount() {
		return amount;
	}

	PaymentStatus getStatus() {
		return status;
	}

	PaymentType getType() {
		return type;
	}
}
