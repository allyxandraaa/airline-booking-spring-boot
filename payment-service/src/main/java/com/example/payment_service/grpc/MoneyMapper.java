package com.example.payment_service.grpc;

import com.example.grpc.common.v1.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class MoneyMapper {

	private static final int NANO_SCALE = 9;

	private MoneyMapper() {
	}

	public static Money toProto(BigDecimal value) {
		BigDecimal scaled = value.setScale(NANO_SCALE, RoundingMode.HALF_UP);
		long units = scaled.longValue();
		int nanos = scaled.subtract(BigDecimal.valueOf(units)).movePointRight(NANO_SCALE).intValueExact();
		return Money.newBuilder().setUnits(units).setNanos(nanos).build();
	}

	public static BigDecimal fromProto(Money money) {
		BigDecimal value = BigDecimal.valueOf(money.getUnits())
				.add(BigDecimal.valueOf(money.getNanos(), NANO_SCALE))
				.stripTrailingZeros();
		return value.scale() < 0 ? value.setScale(0) : value;
	}
}
