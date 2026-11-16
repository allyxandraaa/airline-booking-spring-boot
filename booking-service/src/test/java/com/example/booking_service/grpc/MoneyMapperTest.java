package com.example.booking_service.grpc;

import com.example.grpc.common.v1.Money;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class MoneyMapperTest {

	@ParameterizedTest
	@CsvSource({
			"129.50, 129, 500000000",
			"0.01, 0, 10000000",
			"0, 0, 0",
			"1000000, 1000000, 0",
			"-1.25, -1, -250000000"
	})
	void convertsToUnitsAndNanos(String amount, long units, int nanos) {
		Money money = MoneyMapper.toProto(new BigDecimal(amount));

		assertThat(money.getUnits()).isEqualTo(units);
		assertThat(money.getNanos()).isEqualTo(nanos);
	}

	@ParameterizedTest
	@CsvSource({"129.50", "0.01", "0", "1000000", "-1.25", "0.123456789"})
	void roundTripKeepsTheValue(String amount) {
		BigDecimal original = new BigDecimal(amount);

		BigDecimal restored = MoneyMapper.fromProto(MoneyMapper.toProto(original));

		assertThat(restored).isEqualByComparingTo(original);
	}
}
