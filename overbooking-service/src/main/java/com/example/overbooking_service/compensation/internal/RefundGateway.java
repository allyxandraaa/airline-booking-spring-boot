package com.example.overbooking_service.compensation.internal;

import com.example.overbooking_service.compensation.CompensationResult;

import java.util.List;

interface RefundGateway {

	List<CompensationResult> refundAll(List<RefundOrder> orders);
}
