package com.manas.settlementmatch.goldencopy;

/**
 * The four identifier schemes the golden-copy service's vendor feeds are
 * each independently keyed on. One {@link Vendor} is the native source for
 * each scheme (see {@link Vendor#nativeScheme()}); the REST API also
 * accepts any of the four as a lookup key, via {@link IdentifierCrosswalk}.
 */
public enum IdentifierScheme {
    CUSIP,
    ISIN,
    SEDOL,
    TICKER
}
