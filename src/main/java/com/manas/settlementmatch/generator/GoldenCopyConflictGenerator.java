package com.manas.settlementmatch.generator;

import com.manas.settlementmatch.goldencopy.FieldOutcome;
import com.manas.settlementmatch.goldencopy.GoldenField;
import com.manas.settlementmatch.goldencopy.IdentifierCrosswalk;
import com.manas.settlementmatch.goldencopy.SecurityIdentifiers;
import com.manas.settlementmatch.goldencopy.SurvivorshipRule;
import com.manas.settlementmatch.goldencopy.SurvivorshipRuleSet;
import com.manas.settlementmatch.goldencopy.Vendor;
import com.manas.settlementmatch.goldencopy.VendorFeedRecord;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Builds exactly 40 hand-specified, individually verifiable cross-vendor
 * conflict cases for the "40 of 40 seeded conflicts resolved by the
 * correct rule or held" claim. Unlike {@link VendorFeedGenerator} (random
 * noise at scale, used for the 250,000-record no-silent-merge claim),
 * every one of these 40 cases carries a known, precomputed expected
 * outcome. Scenarios are read off the real, declared
 * {@link SurvivorshipRuleSet} rather than a second, hardcoded copy of the
 * precedence order, so a case's expected outcome always matches what the
 * rule table actually says.
 */
public final class GoldenCopyConflictGenerator {

    public enum ScenarioKind {
        CHAIN_TOP_VENDOR_WINS,
        CHAIN_FALLS_BACK_TO_SECOND_VENDOR,
        CHAIN_EXHAUSTED_HELD,
        NO_RULE_DISAGREEMENT_HELD,
        NO_RULE_CONSENSUS_RESOLVED
    }

    public record ConflictCase(
            int index,
            String internalKey,
            GoldenField targetField,
            ScenarioKind scenario,
            Map<Vendor, VendorFeedRecord> recordsByVendor,
            FieldOutcome expectedOutcome,
            Object expectedValue,
            Vendor expectedVendor,
            String expectedOwner
    ) {
    }

    private static final int CASE_COUNT = 40;

    public List<ConflictCase> generate(IdentifierCrosswalk crosswalk, SurvivorshipRuleSet ruleSet) {
        if (crosswalk.size() < CASE_COUNT) {
            throw new IllegalArgumentException("crosswalk must hold at least " + CASE_COUNT + " securities for the 40 conflict cases");
        }

        List<GoldenField> chainFields = List.of(GoldenField.NAME, GoldenField.COUNTRY, GoldenField.CURRENCY, GoldenField.MATURITY, GoldenField.COUPON);
        List<Map.Entry<GoldenField, ScenarioKind>> templates = new ArrayList<>();
        for (GoldenField field : chainFields) {
            templates.add(new AbstractMap.SimpleEntry<>(field, ScenarioKind.CHAIN_TOP_VENDOR_WINS));
            templates.add(new AbstractMap.SimpleEntry<>(field, ScenarioKind.CHAIN_FALLS_BACK_TO_SECOND_VENDOR));
            templates.add(new AbstractMap.SimpleEntry<>(field, ScenarioKind.CHAIN_EXHAUSTED_HELD));
        }
        templates.add(new AbstractMap.SimpleEntry<>(GoldenField.EXCHANGE, ScenarioKind.CHAIN_TOP_VENDOR_WINS));
        templates.add(new AbstractMap.SimpleEntry<>(GoldenField.EXCHANGE, ScenarioKind.CHAIN_EXHAUSTED_HELD));
        templates.add(new AbstractMap.SimpleEntry<>(GoldenField.SECTOR, ScenarioKind.NO_RULE_DISAGREEMENT_HELD));
        templates.add(new AbstractMap.SimpleEntry<>(GoldenField.SECTOR, ScenarioKind.NO_RULE_CONSENSUS_RESOLVED));

        List<ConflictCase> cases = new ArrayList<>(CASE_COUNT);
        for (int i = 0; i < CASE_COUNT; i++) {
            Map.Entry<GoldenField, ScenarioKind> template = templates.get(i % templates.size());
            cases.add(buildCase(i, crosswalk, ruleSet, template.getKey(), template.getValue()));
        }
        return cases;
    }

    private ConflictCase buildCase(int index, IdentifierCrosswalk crosswalk, SurvivorshipRuleSet ruleSet,
                                    GoldenField field, ScenarioKind scenario) {
        SecurityIdentifiers ids = crosswalk.all().get(index);
        SurvivorshipRule rule = ruleSet.ruleFor(field);
        List<Vendor> chain = rule.precedence();

        Map<Vendor, VendorFeedRecord> byVendor = new EnumMap<>(Vendor.class);
        for (Vendor vendor : Vendor.values()) {
            byVendor.put(vendor, fillerRecord(vendor, ids, index));
        }

        FieldOutcome expectedOutcome;
        Object expectedValue = null;
        Vendor expectedVendor = null;
        String expectedOwner = null;

        switch (scenario) {
            case CHAIN_TOP_VENDOR_WINS -> {
                Vendor top = chain.get(0);
                Object topValue = sampleValue(field, index, "TOP");
                setFieldOnVendor(byVendor, top, field, topValue);
                for (Vendor other : Vendor.values()) {
                    if (other != top) {
                        setFieldOnVendor(byVendor, other, field, sampleValue(field, index, "OTHER-" + other));
                    }
                }
                expectedOutcome = FieldOutcome.RESOLVED;
                expectedValue = topValue;
                expectedVendor = top;
            }
            case CHAIN_FALLS_BACK_TO_SECOND_VENDOR -> {
                Vendor top = chain.get(0);
                Vendor second = chain.get(1);
                setFieldOnVendor(byVendor, top, field, null);
                Object secondValue = sampleValue(field, index, "SECOND");
                setFieldOnVendor(byVendor, second, field, secondValue);
                for (Vendor other : Vendor.values()) {
                    if (other != top && other != second) {
                        setFieldOnVendor(byVendor, other, field, sampleValue(field, index, "OTHER-" + other));
                    }
                }
                expectedOutcome = FieldOutcome.RESOLVED;
                expectedValue = secondValue;
                expectedVendor = second;
            }
            case CHAIN_EXHAUSTED_HELD -> {
                for (Vendor v : chain) {
                    setFieldOnVendor(byVendor, v, field, null);
                }
                expectedOutcome = FieldOutcome.HELD;
                expectedOwner = rule.dataOwner();
            }
            case NO_RULE_DISAGREEMENT_HELD -> {
                setFieldOnVendor(byVendor, Vendor.CUSIP_VENDOR, field, sampleValue(field, index, "A"));
                setFieldOnVendor(byVendor, Vendor.ISIN_VENDOR, field, sampleValue(field, index, "B"));
                setFieldOnVendor(byVendor, Vendor.SEDOL_VENDOR, field, null);
                setFieldOnVendor(byVendor, Vendor.TICKER_VENDOR, field, null);
                expectedOutcome = FieldOutcome.HELD;
                expectedOwner = rule.dataOwner();
            }
            case NO_RULE_CONSENSUS_RESOLVED -> {
                Object agreed = sampleValue(field, index, "AGREED");
                setFieldOnVendor(byVendor, Vendor.CUSIP_VENDOR, field, agreed);
                setFieldOnVendor(byVendor, Vendor.ISIN_VENDOR, field, agreed);
                setFieldOnVendor(byVendor, Vendor.SEDOL_VENDOR, field, null);
                setFieldOnVendor(byVendor, Vendor.TICKER_VENDOR, field, null);
                expectedOutcome = FieldOutcome.RESOLVED;
                expectedValue = agreed;
                expectedVendor = Vendor.CUSIP_VENDOR;
            }
            default -> throw new IllegalStateException("unhandled scenario " + scenario);
        }

        return new ConflictCase(index, ids.internalKey(), field, scenario, byVendor, expectedOutcome, expectedValue, expectedVendor, expectedOwner);
    }

    private VendorFeedRecord fillerRecord(Vendor vendor, SecurityIdentifiers ids, int index) {
        String nativeIdentifier = ids.forScheme(vendor.nativeScheme());
        String vendorRecordId = vendor.recordIdPrefix() + "-FILLER-" + "%08d".formatted(index);
        return new VendorFeedRecord(
                vendor, vendorRecordId, nativeIdentifier,
                "Filler Security " + index, "US", "USD", "Industrials",
                LocalDate.of(2027, 6, 1), BigDecimal.valueOf(2, 2), "NYSE");
    }

    private Object sampleValue(GoldenField field, int index, String tag) {
        int seed = tagSeed(tag);
        return switch (field) {
            case NAME, COUNTRY, CURRENCY, SECTOR, EXCHANGE -> field.fieldName() + "-" + index + "-" + tag;
            case MATURITY -> LocalDate.of(2027, 1, 1).plusDays((long) index * 37 + seed);
            case COUPON -> BigDecimal.valueOf((index % 500) + seed, 3);
        };
    }

    private int tagSeed(String tag) {
        return switch (tag) {
            case "TOP" -> 1;
            case "SECOND" -> 2;
            case "A" -> 3;
            case "B" -> 4;
            case "AGREED" -> 5;
            default -> tag.startsWith("OTHER-") ? 10 + Math.abs(tag.hashCode() % 50) : 99;
        };
    }

    private VendorFeedRecord withField(VendorFeedRecord r, GoldenField field, Object value) {
        return new VendorFeedRecord(
                r.vendor(), r.vendorRecordId(), r.nativeIdentifier(),
                field == GoldenField.NAME ? (String) value : r.name(),
                field == GoldenField.COUNTRY ? (String) value : r.country(),
                field == GoldenField.CURRENCY ? (String) value : r.currency(),
                field == GoldenField.SECTOR ? (String) value : r.sector(),
                field == GoldenField.MATURITY ? (LocalDate) value : r.maturity(),
                field == GoldenField.COUPON ? (BigDecimal) value : r.coupon(),
                field == GoldenField.EXCHANGE ? (String) value : r.exchange());
    }

    private void setFieldOnVendor(Map<Vendor, VendorFeedRecord> byVendor, Vendor vendor, GoldenField field, Object value) {
        byVendor.put(vendor, withField(byVendor.get(vendor), field, value));
    }
}
