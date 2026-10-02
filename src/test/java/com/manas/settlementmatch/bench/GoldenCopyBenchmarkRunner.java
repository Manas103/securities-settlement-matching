package com.manas.settlementmatch.bench;

import com.manas.settlementmatch.generator.GoldenCopyConflictGenerator;
import com.manas.settlementmatch.generator.GoldenCopyConflictGenerator.ConflictCase;
import com.manas.settlementmatch.generator.VendorFeedGenerator;
import com.manas.settlementmatch.goldencopy.FieldOutcome;
import com.manas.settlementmatch.goldencopy.FieldResolution;
import com.manas.settlementmatch.goldencopy.GoldenCopyConsolidationEngine;
import com.manas.settlementmatch.goldencopy.GoldenField;
import com.manas.settlementmatch.goldencopy.GoldenSecurityRecord;
import com.manas.settlementmatch.goldencopy.IdentifierCrosswalk;
import com.manas.settlementmatch.goldencopy.IdentifierScheme;
import com.manas.settlementmatch.goldencopy.SurvivorshipRuleSet;
import com.manas.settlementmatch.goldencopy.SurvivorshipRuleSetLoader;
import com.manas.settlementmatch.goldencopy.Vendor;
import com.manas.settlementmatch.goldencopy.VendorFeedRecord;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The real measurement run behind the golden-copy security master's
 * claims. Tagged {@code benchmark}, excluded from the default {@code mvn
 * test} run, the same pattern as {@link BenchmarkRunner} and
 * {@link CrossFormatBenchmarkRunner}. Run explicitly with:
 * {@code mvn test -Dtest=GoldenCopyBenchmarkRunner -Dsurefire.excludedGroups=}
 */
class GoldenCopyBenchmarkRunner {

    private static final SurvivorshipRuleSet RULE_SET = SurvivorshipRuleSetLoader.loadFromClasspath("goldencopy-survivorship-rules.yml");

    @Test
    @Tag("benchmark")
    void measureGoldenCopyClaims() {
        StringBuilder report = new StringBuilder();
        line(report, "=== Golden-Copy Security Master -- survivorship benchmark run ===");
        line(report, "started: " + Instant.now());

        VendorFeedGenerator feedGenerator = new VendorFeedGenerator();

        // -- claim: four disagreeing simulated vendor feeds, each on its own identifier scheme --
        IdentifierCrosswalk demoCrosswalk = new IdentifierCrosswalk(10);
        Map<Vendor, List<VendorFeedRecord>> demoFeeds = feedGenerator.generate(demoCrosswalk, 0.1, 0.3, 42L).feedsByVendor();

        line(report, "");
        line(report, "-- claim: four disagreeing simulated vendor feeds, each on its own identifier scheme --");
        for (Vendor vendor : Vendor.values()) {
            VendorFeedRecord sample = demoFeeds.get(vendor).get(0);
            line(report, vendor + " (" + vendor.displayName() + "), native scheme " + vendor.nativeScheme()
                    + ", " + demoFeeds.get(vendor).size() + " records, sample native id = " + sample.nativeIdentifier());
        }
        boolean fourFeedsExist = demoFeeds.size() == 4 && demoFeeds.values().stream().allMatch(l -> !l.isEmpty());
        long genuineDisagreements = countFieldDisagreements(demoFeeds, demoCrosswalk);
        line(report, "genuine field-level cross-vendor disagreements present in this 10-security demo sample: " + genuineDisagreements);
        line(report, "result: " + (fourFeedsExist && genuineDisagreements > 0));

        // -- claim: identifier cross-reference layer spans CUSIP, ISIN, SEDOL, ticker --
        line(report, "");
        line(report, "-- claim: each vendor feed keyed on its own identifier scheme (CUSIP / ISIN / SEDOL / ticker) --");
        var sampleIds = demoCrosswalk.all().get(0);
        line(report, "security " + sampleIds.internalKey() + ": cusip=" + sampleIds.cusip() + " isin=" + sampleIds.isin()
                + " sedol=" + sampleIds.sedol() + " ticker=" + sampleIds.ticker());
        boolean allFourSchemesResolve = true;
        for (IdentifierScheme scheme : IdentifierScheme.values()) {
            allFourSchemesResolve &= demoCrosswalk.resolveToInternalKey(scheme, sampleIds.forScheme(scheme)).isPresent();
        }
        line(report, "all four schemes resolve back to the same internal key: " + allFourSchemesResolve);

        // -- claim: declared per-field survivorship precedence --
        line(report, "");
        line(report, "-- claim: declared per-field survivorship precedence (goldencopy-survivorship-rules.yml) --");
        for (GoldenField field : GoldenField.values()) {
            var rule = RULE_SET.ruleFor(field);
            line(report, field.fieldName() + ": precedence=" + rule.precedence() + " dataOwner=\"" + rule.dataOwner() + "\"");
        }
        line(report, "(the held-for-named-owner path itself is exercised by GoldenCopyConsolidationEngineTest; "
                + "see docs/golden_copy_test_output.txt)");

        // -- claim: 40 of 40 seeded cross-vendor conflicts resolved by the correct rule or held --
        GoldenCopyConflictGenerator conflictGenerator = new GoldenCopyConflictGenerator();
        IdentifierCrosswalk conflictCrosswalk = new IdentifierCrosswalk(40);
        List<ConflictCase> cases = conflictGenerator.generate(conflictCrosswalk, RULE_SET);
        GoldenCopyConsolidationEngine conflictEngine = new GoldenCopyConsolidationEngine(conflictCrosswalk, RULE_SET);

        int correct = 0;
        List<String> mismatches = new ArrayList<>();
        for (ConflictCase c : cases) {
            Map<Vendor, List<VendorFeedRecord>> feedsByVendor = new EnumMap<>(Vendor.class);
            for (Vendor vendor : Vendor.values()) {
                feedsByVendor.put(vendor, List.of(c.recordsByVendor().get(vendor)));
            }
            GoldenSecurityRecord record = conflictEngine.consolidate(feedsByVendor).get(0);
            FieldResolution resolution = record.resolutionFor(c.targetField());

            boolean matches = resolution.outcome() == c.expectedOutcome()
                    && Objects.equals(resolution.sourceVendor(), c.expectedVendor())
                    && (c.expectedOutcome() == FieldOutcome.HELD
                            ? Objects.equals(resolution.heldForOwner(), c.expectedOwner())
                            : Objects.equals(resolution.value(), c.expectedValue()));
            if (matches) {
                correct++;
            } else {
                mismatches.add("case " + c.index() + " field=" + c.targetField() + " scenario=" + c.scenario()
                        + " expected=" + c.expectedOutcome() + "/" + c.expectedValue() + "/" + c.expectedVendor()
                        + " actual=" + resolution.outcome() + "/" + resolution.value() + "/" + resolution.sourceVendor());
            }
        }

        line(report, "");
        line(report, "-- claim: 40 of 40 seeded cross-vendor conflicts resolved by the correct rule or held --");
        line(report, "correct: " + correct + " / " + cases.size());
        for (String m : mismatches) {
            line(report, "MISMATCH: " + m);
        }

        // -- claim: 0 silent merges over 250,000 records --
        int recordCount = 250_000;
        IdentifierCrosswalk bigCrosswalk = new IdentifierCrosswalk(recordCount);
        Map<Vendor, List<VendorFeedRecord>> bigFeeds = feedGenerator.generate(bigCrosswalk, 0.05, 0.02, 20260715L).feedsByVendor();
        GoldenCopyConsolidationEngine bigEngine = new GoldenCopyConsolidationEngine(bigCrosswalk, RULE_SET);

        Instant consolidateStart = Instant.now();
        List<GoldenSecurityRecord> bigGolden = bigEngine.consolidate(bigFeeds);
        Instant consolidateEnd = Instant.now();

        long silentMerges = countSilentMerges(bigGolden, bigFeeds, bigCrosswalk);
        long heldFields = bigGolden.stream().flatMap(r -> r.fieldResolutions().stream()).filter(r -> r.outcome() == FieldOutcome.HELD).count();
        long resolvedFields = bigGolden.stream().flatMap(r -> r.fieldResolutions().stream()).filter(r -> r.outcome() == FieldOutcome.RESOLVED).count();

        line(report, "");
        line(report, "-- claim: 0 silent merges over 250,000 records --");
        line(report, "securities consolidated: " + bigGolden.size() + " / " + recordCount);
        line(report, "consolidation wall time: " + Duration.between(consolidateStart, consolidateEnd).toMillis() + " ms");
        line(report, "total golden fields evaluated: " + (resolvedFields + heldFields));
        line(report, "resolved fields: " + resolvedFields);
        line(report, "held fields: " + heldFields);
        line(report, "silent merges detected: " + silentMerges);

        line(report, "");
        line(report, "(the exact-agreement-with-reference-oracle claim is measured by "
                + "GoldenCopyReferenceOracleDiffTest at a 400-security sample, part of the default mvn test run; "
                + "see docs/golden_copy_test_output.txt)");

        line(report, "");
        line(report, "finished: " + Instant.now());
        System.out.println(report);

        assertThat(fourFeedsExist).isTrue();
        assertThat(genuineDisagreements).isGreaterThan(0);
        assertThat(allFourSchemesResolve).isTrue();
        assertThat(correct).isEqualTo(cases.size());
        assertThat(silentMerges).isZero();
    }

    private long countFieldDisagreements(Map<Vendor, List<VendorFeedRecord>> feeds, IdentifierCrosswalk crosswalk) {
        long count = 0;
        for (int i = 0; i < crosswalk.size(); i++) {
            for (GoldenField field : GoldenField.values()) {
                Object firstNonNull = null;
                boolean disagree = false;
                for (Vendor vendor : Vendor.values()) {
                    Object v = field.valueFrom(feeds.get(vendor).get(i));
                    if (v == null) {
                        continue;
                    }
                    if (firstNonNull == null) {
                        firstNonNull = v;
                    } else if (!firstNonNull.equals(v)) {
                        disagree = true;
                    }
                }
                if (disagree) {
                    count++;
                }
            }
        }
        return count;
    }

    private long countSilentMerges(List<GoldenSecurityRecord> goldenRecords, Map<Vendor, List<VendorFeedRecord>> feeds, IdentifierCrosswalk crosswalk) {
        Map<String, Map<Vendor, VendorFeedRecord>> byKey = new HashMap<>();
        for (Vendor vendor : Vendor.values()) {
            for (VendorFeedRecord r : feeds.get(vendor)) {
                crosswalk.resolveToInternalKey(vendor.nativeScheme(), r.nativeIdentifier())
                        .ifPresent(key -> byKey.computeIfAbsent(key, k -> new EnumMap<>(Vendor.class)).put(vendor, r));
            }
        }
        long silentMerges = 0;
        for (GoldenSecurityRecord golden : goldenRecords) {
            Map<Vendor, VendorFeedRecord> vendorRecords = byKey.get(golden.internalKey());
            for (FieldResolution resolution : golden.fieldResolutions()) {
                if (resolution.outcome() != FieldOutcome.RESOLVED) {
                    continue;
                }
                VendorFeedRecord claimedSource = vendorRecords.get(resolution.sourceVendor());
                boolean ok = claimedSource != null
                        && claimedSource.vendorRecordId().equals(resolution.sourceVendorRecordId())
                        && Objects.equals(resolution.value(), resolution.field().valueFrom(claimedSource));
                if (!ok) {
                    silentMerges++;
                }
            }
        }
        return silentMerges;
    }

    private void line(StringBuilder sb, String text) {
        sb.append(text).append(System.lineSeparator());
    }
}
