package com.manas.settlementmatch.goldencopy;

/**
 * The outcome of resolving one golden field for one security: either
 * {@code RESOLVED} (a literal, unmodified value copied from {@code sourceVendor}'s
 * own {@code sourceVendorRecordId}), or {@code HELD} (no rule could resolve
 * it, so it is named for {@code heldForOwner} instead of guessed).
 */
public record FieldResolution(
        GoldenField field,
        FieldOutcome outcome,
        Object value,
        Vendor sourceVendor,
        String sourceVendorRecordId,
        String heldForOwner
) {
    public String renderedValue() {
        return value == null ? null : String.valueOf(value);
    }
}
