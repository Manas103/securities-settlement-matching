package com.manas.settlementmatch.goldencopy;

/**
 * One underlying security's identifiers across all four schemes, as
 * produced by {@link IdentifierCrosswalk}. {@code internalKey} is not a
 * real-world identifier; it is this service's own synthetic join key,
 * never exposed as if it were a CUSIP, ISIN, SEDOL or ticker in its own
 * right.
 */
public record SecurityIdentifiers(String internalKey, String cusip, String isin, String sedol, String ticker) {

    public String forScheme(IdentifierScheme scheme) {
        return switch (scheme) {
            case CUSIP -> cusip;
            case ISIN -> isin;
            case SEDOL -> sedol;
            case TICKER -> ticker;
        };
    }
}
