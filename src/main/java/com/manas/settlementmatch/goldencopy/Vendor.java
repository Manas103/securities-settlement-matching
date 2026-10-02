package com.manas.settlementmatch.goldencopy;

/**
 * The four simulated vendor reference feeds this golden-copy service
 * consolidates, each independently keyed on its own identifier scheme. All
 * four names are fictional; there is no real data relationship with any
 * actual vendor of this name.
 */
public enum Vendor {
    CUSIP_VENDOR("Argus Reference Data", "ARG", IdentifierScheme.CUSIP),
    ISIN_VENDOR("Meridian Securities Data", "MER", IdentifierScheme.ISIN),
    SEDOL_VENDOR("Northbridge Market Data", "NBM", IdentifierScheme.SEDOL),
    TICKER_VENDOR("Coastline Ticker Feed", "CTF", IdentifierScheme.TICKER);

    private final String displayName;
    private final String recordIdPrefix;
    private final IdentifierScheme nativeScheme;

    Vendor(String displayName, String recordIdPrefix, IdentifierScheme nativeScheme) {
        this.displayName = displayName;
        this.recordIdPrefix = recordIdPrefix;
        this.nativeScheme = nativeScheme;
    }

    public String displayName() {
        return displayName;
    }

    public String recordIdPrefix() {
        return recordIdPrefix;
    }

    public IdentifierScheme nativeScheme() {
        return nativeScheme;
    }
}
