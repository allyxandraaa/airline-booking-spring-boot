package com.example.overbooking_service.resolution.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface OverbookingReportRepository extends JpaRepository<OverbookingReport, UUID> {
}
