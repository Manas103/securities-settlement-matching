package com.manas.settlementmatch.tolerance;

/**
 * How a single field is compared between the two sides of an instruction.
 * The type, and any numeric threshold that goes with it, is data (loaded
 * from {@code tolerance-rules.yml}), not a hardcoded branch in the matcher.
 */
public enum ComparisonType {
    /** Values must be identical (string/date equality). */
    EXACT,
    /** Numeric values must differ by no more than a fixed absolute amount. */
    ABSOLUTE,
    /** Numeric values must differ by no more than a relative amount, expressed in basis points of the average of the two values. */
    RELATIVE_BPS
}
