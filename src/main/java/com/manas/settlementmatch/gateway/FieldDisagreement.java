package com.manas.settlementmatch.gateway;

/**
 * One canonical field on which the FIX, FpML-style, and delimited
 * descriptions of the same trade reference disagree. Unlike
 * {@link com.manas.settlementmatch.tolerance.FieldDiscrepancy} (which
 * compares two counterparties' legs of an already-normalized instruction
 * against a configurable tolerance), this is a three-way, exact-value
 * comparison between re-encodings of what should be the identical trade
 * fact: there is no tolerance concept here, because a FIX allocation and
 * its own FpML-style confirmation are not two counterparties' independent
 * views, they are the same desk describing the same trade twice. Any
 * disagreement at all is a data-quality problem to quarantine, not a
 * pricing discrepancy to tolerance-check.
 */
public record FieldDisagreement(
        String fieldName,
        String fixValue,
        String fpmlValue,
        String delimitedValue
) {
    public String describe() {
        return "%s: fix=%s fpml=%s delimited=%s".formatted(fieldName, fixValue, fpmlValue, delimitedValue);
    }
}
