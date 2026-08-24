package com.manas.settlementmatch.tolerance;

/**
 * One disagreeing field between the two sides of a settlement instruction.
 * This is the object the Break naming requirement is built around: which
 * field, what each side said, how far apart they were, and the tolerance
 * that was exceeded. Every value is pre-formatted to a human-readable
 * string so a Break can be read directly off the database without a caller
 * having to re-derive what "disagreed" meant.
 */
public record FieldDiscrepancy(
        String fieldName,
        String partyAValue,
        String partyBValue,
        String delta,
        String toleranceDescription
) {
    public String describe() {
        return "%s: partyA=%s partyB=%s delta=%s tolerance=%s"
                .formatted(fieldName, partyAValue, partyBValue, delta, toleranceDescription);
    }
}
