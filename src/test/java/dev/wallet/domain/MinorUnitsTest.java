package dev.wallet.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class MinorUnitsTest {

	@Test
	void parsesDecimalStringsToWholeMinorUnits() {
		assertThat(MinorUnits.fromDecimal("10.00").value()).isEqualTo(1000);
		assertThat(MinorUnits.fromDecimal("10").value()).isEqualTo(1000);
		assertThat(MinorUnits.fromDecimal("0.01").value()).isEqualTo(1);
	}

	@ParameterizedTest
	@NullSource
	@ValueSource(strings = {"-5", "0", "0.00", "abc", "1.234", "1,50", " 1"})
	void rejectsInvalidDecimalStrings(String invalid) {
		assertThatIllegalArgumentException()
				.isThrownBy(() -> MinorUnits.fromDecimal(invalid));
	}

	@Test
	void rejectsDecimalsTooLargeToRepresent() {
		assertThatIllegalArgumentException()
				.isThrownBy(() -> MinorUnits.fromDecimal("99999999999999999999"));
	}

	@Test
	void formatsMinorUnitsBackToTwoDecimalPlaces() {
		assertThat(new MinorUnits(1000).toDecimalString()).isEqualTo("10.00");
		assertThat(new MinorUnits(1).toDecimalString()).isEqualTo("0.01");
		assertThat(new MinorUnits(123_456_789L).toDecimalString()).isEqualTo("1234567.89");
	}

	@Test
	void hasValueSemantics() {
		assertThat(new MinorUnits(10)).isEqualTo(MinorUnits.fromDecimal("0.10"));
		assertThat(new MinorUnits(10).hashCode()).isEqualTo(MinorUnits.fromDecimal("0.10").hashCode());
	}
}