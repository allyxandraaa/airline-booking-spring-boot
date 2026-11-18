package com.example.overbooking_service.config;

import com.example.grpc.booking.v1.BookingServiceGrpc;
import com.example.grpc.payment.v1.PaymentServiceGrpc;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.client.ImportGrpcClients;

@Configuration
@ImportGrpcClients(target = "booking", types = BookingServiceGrpc.BookingServiceBlockingStub.class)
@ImportGrpcClients(target = "payment", types = PaymentServiceGrpc.PaymentServiceStub.class)
class GrpcClientConfig {
}
