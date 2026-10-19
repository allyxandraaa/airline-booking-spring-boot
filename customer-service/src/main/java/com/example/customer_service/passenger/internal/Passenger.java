package com.example.customer_service.passenger.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "passengers")
class Passenger {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false)
	private String fullName;

	@Column(nullable = false)
	private String email;

	@Column(nullable = false)
	private String phone;

	@Column(nullable = false)
	private String documentNumber;

	@Column(nullable = false)
	private UUID savedPaymentMethodId;

	protected Passenger() {
	}

	Passenger(String fullName, String email, String phone, String documentNumber, UUID savedPaymentMethodId) {
		this.fullName = fullName;
		this.email = email;
		this.phone = phone;
		this.documentNumber = documentNumber;
		this.savedPaymentMethodId = savedPaymentMethodId;
	}

	public UUID getId() {
		return id;
	}

	public String getFullName() {
		return fullName;
	}

	public String getEmail() {
		return email;
	}

	public String getPhone() {
		return phone;
	}

	public String getDocumentNumber() {
		return documentNumber;
	}

	public UUID getSavedPaymentMethodId() {
		return savedPaymentMethodId;
	}
}
