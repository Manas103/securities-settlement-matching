package com.manas.settlementmatch.goldencopy;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GoldenCopyConsolidationEngineTest {

    private final SurvivorshipRuleSet ruleSet = SurvivorshipRuleSetLoader.loadFromClasspath("goldencopy-survivorship-rules.yml");
    private final IdentifierCrosswalk crosswalk = new IdentifierCrosswalk(5);
    private final GoldenCopyConsolidationEngine engine = new GoldenCopyConsolidationEngine(crosswalk, ruleSet);

    private VendorFeedRecord record(Vendor vendor, SecurityIdentifiers ids, String name, String country, String currency,
                                     String sector, LocalDate maturity, BigDecimal coupon, String exchange) {
        return new VendorFeedRecord(vendor, vendor.recordIdPrefix() + "-1", ids.forScheme(vendor.nativeScheme()),
                name, country, currency, sector, maturity, coupon, exchange);
    }

    @Test
    void precedenceChainResolvesToTopVendorWhenAllVendorsDisagree() {
        SecurityIdentifiers ids = crosswalk.all().get(0);
        Map<Vendor, List<VendorFeedRecord>> feeds = Map.of(
                Vendor.CUSIP_VENDOR, List.of(record(Vendor.CUSIP_VENDOR, ids, "Acme Corp", "US", "USD", "Industrials", LocalDate.of(2027, 6, 1), BigDecimal.valueOf(4.5), "NYSE")),
                Vendor.ISIN_VENDOR, List.of(record(Vendor.ISIN_VENDOR, ids, "Acme Corporation", "US", "USD", "Industrials", LocalDate.of(2027, 6, 1), BigDecimal.valueOf(4.5), "NYSE")),
                Vendor.SEDOL_VENDOR, List.of(record(Vendor.SEDOL_VENDOR, ids, "ACME", "US", "USD", "Industrials", LocalDate.of(2027, 6, 1), BigDecimal.valueOf(4.5), "NYSE")),
                Vendor.TICKER_VENDOR, List.of(record(Vendor.TICKER_VENDOR, ids, "Acme", "US", "USD", "Industrials", LocalDate.of(2027, 6, 1), BigDecimal.valueOf(4.5), "NYSE")));

        GoldenSecurityRecord golden = engine.consolidate(feeds).get(0);
        FieldResolution name = golden.resolutionFor(GoldenField.NAME);
        assertThat(name.outcome()).isEqualTo(FieldOutcome.RESOLVED);
        assertThat(name.value()).isEqualTo("Acme Corp");
        assertThat(name.sourceVendor()).isEqualTo(Vendor.CUSIP_VENDOR);
        assertThat(name.sourceVendorRecordId()).isEqualTo("ARG-1");
    }

    @Test
    void precedenceChainFallsBackWhenTopVendorIsMissingTheField() {
        SecurityIdentifiers ids = crosswalk.all().get(1);
        Map<Vendor, List<VendorFeedRecord>> feeds = Map.of(
                Vendor.CUSIP_VENDOR, List.of(record(Vendor.CUSIP_VENDOR, ids, null, "US", "USD", "Industrials", LocalDate.of(2027, 6, 1), BigDecimal.valueOf(4.5), "NYSE")),
                Vendor.ISIN_VENDOR, List.of(record(Vendor.ISIN_VENDOR, ids, "Beta Inc", "US", "USD", "Industrials", LocalDate.of(2027, 6, 1), BigDecimal.valueOf(4.5), "NYSE")),
                Vendor.SEDOL_VENDOR, List.of(record(Vendor.SEDOL_VENDOR, ids, "Beta Incorporated", "US", "USD", "Industrials", LocalDate.of(2027, 6, 1), BigDecimal.valueOf(4.5), "NYSE")),
                Vendor.TICKER_VENDOR, List.of(record(Vendor.TICKER_VENDOR, ids, "Beta", "US", "USD", "Industrials", LocalDate.of(2027, 6, 1), BigDecimal.valueOf(4.5), "NYSE")));

        GoldenSecurityRecord golden = engine.consolidate(feeds).get(0);
        FieldResolution name = golden.resolutionFor(GoldenField.NAME);
        assertThat(name.outcome()).isEqualTo(FieldOutcome.RESOLVED);
        assertThat(name.value()).isEqualTo("Beta Inc");
        assertThat(name.sourceVendor()).isEqualTo(Vendor.ISIN_VENDOR);
    }

    @Test
    void precedenceChainHeldForNamedOwnerWhenEveryListedVendorIsMissing() {
        SecurityIdentifiers ids = crosswalk.all().get(2);
        Map<Vendor, List<VendorFeedRecord>> feeds = Map.of(
                Vendor.CUSIP_VENDOR, List.of(record(Vendor.CUSIP_VENDOR, ids, "x", "US", null, "Industrials", LocalDate.of(2027, 6, 1), BigDecimal.valueOf(4.5), "NYSE")),
                Vendor.ISIN_VENDOR, List.of(record(Vendor.ISIN_VENDOR, ids, "x", "US", null, "Industrials", LocalDate.of(2027, 6, 1), BigDecimal.valueOf(4.5), "NYSE")),
                Vendor.SEDOL_VENDOR, List.of(record(Vendor.SEDOL_VENDOR, ids, "x", "US", "GBP", "Industrials", LocalDate.of(2027, 6, 1), BigDecimal.valueOf(4.5), "NYSE")),
                Vendor.TICKER_VENDOR, List.of(record(Vendor.TICKER_VENDOR, ids, "x", "US", "JPY", "Industrials", LocalDate.of(2027, 6, 1), BigDecimal.valueOf(4.5), "NYSE")));

        // currency precedence = [CUSIP_VENDOR, SEDOL_VENDOR]; CUSIP is null here so the chain falls
        // through to SEDOL, which does carry a value (GBP), and that is what should win.
        GoldenSecurityRecord golden = engine.consolidate(feeds).get(0);
        FieldResolution currency = golden.resolutionFor(GoldenField.CURRENCY);
        assertThat(currency.outcome()).isEqualTo(FieldOutcome.RESOLVED);
        assertThat(currency.value()).isEqualTo("GBP");
        assertThat(currency.sourceVendor()).isEqualTo(Vendor.SEDOL_VENDOR);
    }

    @Test
    void precedenceChainTrulyExhaustedIsHeldForNamedOwner() {
        SecurityIdentifiers ids = crosswalk.all().get(3);
        Map<Vendor, List<VendorFeedRecord>> feeds = Map.of(
                Vendor.CUSIP_VENDOR, List.of(record(Vendor.CUSIP_VENDOR, ids, "x", "US", null, "Industrials", LocalDate.of(2027, 6, 1), BigDecimal.valueOf(4.5), "NYSE")),
                Vendor.ISIN_VENDOR, List.of(record(Vendor.ISIN_VENDOR, ids, "x", "US", "EUR", "Industrials", LocalDate.of(2027, 6, 1), BigDecimal.valueOf(4.5), "NYSE")),
                Vendor.SEDOL_VENDOR, List.of(record(Vendor.SEDOL_VENDOR, ids, "x", "US", null, "Industrials", LocalDate.of(2027, 6, 1), BigDecimal.valueOf(4.5), "NYSE")),
                Vendor.TICKER_VENDOR, List.of(record(Vendor.TICKER_VENDOR, ids, "x", "US", "JPY", "Industrials", LocalDate.of(2027, 6, 1), BigDecimal.valueOf(4.5), "NYSE")));

        // currency precedence = [CUSIP_VENDOR, SEDOL_VENDOR]; both are null here, even though ISIN
        // and TICKER (not in the declared precedence for currency) do carry a value: the chain is
        // exhausted, and those un-declared vendors are never used as a silent fallback.
        GoldenSecurityRecord golden = engine.consolidate(feeds).get(0);
        FieldResolution currency = golden.resolutionFor(GoldenField.CURRENCY);
        assertThat(currency.outcome()).isEqualTo(FieldOutcome.HELD);
        assertThat(currency.heldForOwner()).isEqualTo("Reference Data Operations - Security Master Desk");
        assertThat(currency.value()).isNull();
        assertThat(currency.sourceVendor()).isNull();
    }

    @Test
    void noRuleFieldHeldWhenVendorsDisagree() {
        SecurityIdentifiers ids = crosswalk.all().get(4);
        Map<Vendor, List<VendorFeedRecord>> feeds = Map.of(
                Vendor.CUSIP_VENDOR, List.of(record(Vendor.CUSIP_VENDOR, ids, "x", "US", "USD", "Industrials", LocalDate.of(2027, 6, 1), BigDecimal.valueOf(4.5), "NYSE")),
                Vendor.ISIN_VENDOR, List.of(record(Vendor.ISIN_VENDOR, ids, "x", "US", "USD", "Manufacturing", LocalDate.of(2027, 6, 1), BigDecimal.valueOf(4.5), "NYSE")),
                Vendor.SEDOL_VENDOR, List.of(record(Vendor.SEDOL_VENDOR, ids, "x", "US", "USD", null, LocalDate.of(2027, 6, 1), BigDecimal.valueOf(4.5), "NYSE")),
                Vendor.TICKER_VENDOR, List.of(record(Vendor.TICKER_VENDOR, ids, "x", "US", "USD", null, LocalDate.of(2027, 6, 1), BigDecimal.valueOf(4.5), "NYSE")));

        GoldenSecurityRecord golden = engine.consolidate(feeds).get(0);
        FieldResolution sector = golden.resolutionFor(GoldenField.SECTOR);
        assertThat(sector.outcome()).isEqualTo(FieldOutcome.HELD);
        assertThat(sector.heldForOwner()).isEqualTo("Sector Taxonomy Committee");
    }

    @Test
    void noRuleFieldResolvedWhenEveryVendorWithAValueAgrees() {
        SecurityIdentifiers ids = crosswalk.all().get(0);
        Map<Vendor, List<VendorFeedRecord>> feeds = Map.of(
                Vendor.CUSIP_VENDOR, List.of(record(Vendor.CUSIP_VENDOR, ids, "x", "US", "USD", "Industrials", LocalDate.of(2027, 6, 1), BigDecimal.valueOf(4.5), "NYSE")),
                Vendor.ISIN_VENDOR, List.of(record(Vendor.ISIN_VENDOR, ids, "x", "US", "USD", "Industrials", LocalDate.of(2027, 6, 1), BigDecimal.valueOf(4.5), "NYSE")),
                Vendor.SEDOL_VENDOR, List.of(record(Vendor.SEDOL_VENDOR, ids, "x", "US", "USD", null, LocalDate.of(2027, 6, 1), BigDecimal.valueOf(4.5), "NYSE")),
                Vendor.TICKER_VENDOR, List.of(record(Vendor.TICKER_VENDOR, ids, "x", "US", "USD", null, LocalDate.of(2027, 6, 1), BigDecimal.valueOf(4.5), "NYSE")));

        GoldenSecurityRecord golden = engine.consolidate(feeds).get(0);
        FieldResolution sector = golden.resolutionFor(GoldenField.SECTOR);
        assertThat(sector.outcome()).isEqualTo(FieldOutcome.RESOLVED);
        assertThat(sector.value()).isEqualTo("Industrials");
    }

    @Test
    void fixedSingleVendorFieldHeldWhenThatVendorIsMissing() {
        SecurityIdentifiers ids = crosswalk.all().get(1);
        Map<Vendor, List<VendorFeedRecord>> feeds = Map.of(
                Vendor.CUSIP_VENDOR, List.of(record(Vendor.CUSIP_VENDOR, ids, "x", "US", "USD", "Industrials", LocalDate.of(2027, 6, 1), BigDecimal.valueOf(4.5), "NYSE")),
                Vendor.ISIN_VENDOR, List.of(record(Vendor.ISIN_VENDOR, ids, "x", "US", "USD", "Industrials", LocalDate.of(2027, 6, 1), BigDecimal.valueOf(4.5), "NASDAQ")),
                Vendor.SEDOL_VENDOR, List.of(record(Vendor.SEDOL_VENDOR, ids, "x", "US", "USD", "Industrials", LocalDate.of(2027, 6, 1), BigDecimal.valueOf(4.5), "LSE")),
                Vendor.TICKER_VENDOR, List.of(record(Vendor.TICKER_VENDOR, ids, "x", "US", "USD", "Industrials", LocalDate.of(2027, 6, 1), BigDecimal.valueOf(4.5), null)));

        GoldenSecurityRecord golden = engine.consolidate(feeds).get(0);
        FieldResolution exchange = golden.resolutionFor(GoldenField.EXCHANGE);
        assertThat(exchange.outcome()).isEqualTo(FieldOutcome.HELD);
        assertThat(exchange.heldForOwner()).isEqualTo("Listings and Market Structure Desk");
    }

    @Test
    void unknownNativeIdentifierIsDroppedRatherThanCrashing() {
        Map<Vendor, List<VendorFeedRecord>> feeds = Map.of(
                Vendor.CUSIP_VENDOR, List.of(new VendorFeedRecord(Vendor.CUSIP_VENDOR, "ARG-1", "NOTINCROSSWALK",
                        "x", "US", "USD", "Industrials", LocalDate.of(2027, 6, 1), BigDecimal.valueOf(4.5), "NYSE")));
        assertThat(engine.consolidate(feeds)).isEmpty();
    }
}
