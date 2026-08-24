package com.manas.settlementmatch.tolerance;

import java.math.BigDecimal;

/**
 * The configured comparison for one field. {@code toleranceValue} is
 * interpreted according to {@code comparison}: unused for EXACT, an absolute
 * amount for ABSOLUTE, and a basis-point count for RELATIVE_BPS.
 */
public record ToleranceRule(String fieldName, ComparisonType comparison, BigDecimal toleranceValue) {
}
