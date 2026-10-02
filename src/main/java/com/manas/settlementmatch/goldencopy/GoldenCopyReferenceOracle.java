package com.manas.settlementmatch.goldencopy;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A deliberately slow, deliberately unindexed golden-copy oracle, diffed
 * exactly against {@link GoldenCopyConsolidationEngine} per BUILDER.md
 * section 2. Grouping is nested linear scans (collect distinct keys by
 * scanning every vendor's list, then re-scan every vendor's list again per
 * key), no {@code HashMap} keyed by internal key anywhere in this class.
 * Field resolution itself is not reimplemented naively a second time: it
 * calls the same {@link FieldSurvivorshipResolver} the fast engine calls,
 * exactly the discipline {@code ReferenceOracleReconciliationGate} already
 * follows with {@code FieldComparator} in this repository. The
 * {@code EnumMap} built per security below is a small return-value
 * carrier handed to the shared resolver, not the grouping logic under
 * test; the O(n^2)-ish nested scans above it are.
 */
public final class GoldenCopyReferenceOracle {

    private final IdentifierCrosswalk crosswalk;
    private final SurvivorshipRuleSet ruleSet;

    public GoldenCopyReferenceOracle(IdentifierCrosswalk crosswalk, SurvivorshipRuleSet ruleSet) {
        this.crosswalk = crosswalk;
        this.ruleSet = ruleSet;
    }

    public List<GoldenSecurityRecord> consolidate(Map<Vendor, List<VendorFeedRecord>> feedsByVendor) {
        List<String> internalKeys = new ArrayList<>();
        for (Vendor vendor : Vendor.values()) {
            for (VendorFeedRecord record : feedsByVendor.getOrDefault(vendor, List.of())) {
                Optional<String> key = crosswalk.resolveToInternalKey(vendor.nativeScheme(), record.nativeIdentifier());
                if (key.isEmpty()) {
                    continue;
                }
                if (!internalKeys.contains(key.get())) {
                    internalKeys.add(key.get());
                }
            }
        }

        List<GoldenSecurityRecord> golden = new ArrayList<>(internalKeys.size());
        for (String internalKey : internalKeys) {
            Map<Vendor, VendorFeedRecord> byVendor = new EnumMap<>(Vendor.class);
            for (Vendor vendor : Vendor.values()) {
                VendorFeedRecord found = null;
                for (VendorFeedRecord record : feedsByVendor.getOrDefault(vendor, List.of())) {
                    Optional<String> key = crosswalk.resolveToInternalKey(vendor.nativeScheme(), record.nativeIdentifier());
                    if (key.isPresent() && key.get().equals(internalKey)) {
                        found = record;
                    }
                }
                if (found != null) {
                    byVendor.put(vendor, found);
                }
            }

            SecurityIdentifiers identifiers = crosswalk.identifiersFor(internalKey)
                    .orElseThrow(() -> new IllegalStateException("crosswalk missing identifiers for " + internalKey));
            List<FieldResolution> resolutions = new ArrayList<>(GoldenField.values().length);
            for (GoldenField field : GoldenField.values()) {
                resolutions.add(FieldSurvivorshipResolver.resolve(field, byVendor, ruleSet));
            }
            golden.add(new GoldenSecurityRecord(internalKey, identifiers, resolutions));
        }
        return golden;
    }
}
