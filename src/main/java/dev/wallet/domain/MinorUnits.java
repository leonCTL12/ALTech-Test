package dev.wallet.domain;

import java.util.Locale;
import java.util.regex.Pattern;

public record MinorUnits(long value) {

	private static final Pattern DECIMAL_PATTERN = Pattern.compile("\\d+(\\.\\d{1,2})?");

	public MinorUnits {
		if (value <= 0) {
			throw new IllegalArgumentException("Amount must be a positive whole number of minor units, was: " + value);
		}
	}

	public static MinorUnits fromDecimal(String decimal) {
		if (decimal == null || !DECIMAL_PATTERN.matcher(decimal).matches()) {
			throw new IllegalArgumentException(
					"Amount must be a decimal string with at most two fractional digits: " + decimal);
		}
		try {
			long whole;
			long fraction = 0;
			int dot = decimal.indexOf('.');
			if (dot == -1) {
				whole = Long.parseLong(decimal);
			} else {
				whole = Long.parseLong(decimal.substring(0, dot));
				String fractionDigits = decimal.substring(dot + 1);
				fraction = fractionDigits.length() == 1
						? (fractionDigits.charAt(0) - '0') * 10L
						: Long.parseLong(fractionDigits);
			}
			return new MinorUnits(Math.addExact(Math.multiplyExact(whole, 100L), fraction));
		} catch (NumberFormatException | ArithmeticException e) {
			throw new IllegalArgumentException(
					"Amount exceeds the maximum representable value: " + decimal, e);
		}
	}

	public String toDecimalString() {
		return String.format(Locale.ROOT, "%d.%02d", value / 100, value % 100);
	}
}