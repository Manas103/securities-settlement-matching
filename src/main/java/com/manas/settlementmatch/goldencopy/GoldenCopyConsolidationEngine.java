package com.manas.settlementmatch.goldencopy;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The real golden-copy consolidation engine: hash-indexed, groups each
 * vendor's records into one security by crosswalking its native identifier
 * to the internal key, then resolves every golden field through
 * {@link FieldSurvivorshipResolver} against the declared
 * {@link SurvivorshipRuleSet}. Never averages, interpolates, or otherwise
 * fabricates a value: every resolved field is a literal, unmodified value
 * copied from exactly one vendor's own record, with that vendor's own
 * record id attached as lineage; every field the rules cannot resolve is
 * held for a named data owner instead.
 */
public final class GoldenCopyConsolidationEngine {

    private final IdentifierCrosswalk crosswalk;
    private final SurvivorshipRuleSet ruleSet;

    public GoldenCopyConsolidationEngine(IdentifierCrosswalk crosswalk, SurvivorshipRuleSet ruleSet) {
        this.crosswalk = crosswalk;
        this.ruleSet = ruleSet;
    }

    public List<GoldenSecurityRecord> consolidate(Map<Vendor, List<VendorFeedRecord>> feedsByVendor) {
        Map<String, Map<Vendor, VendorFeedRecord>> byInternalKey = new LinkedHashMap<>();

        for (Map.Entry<Vendor, List<VendorFeedRecord>> entry : feedsByVendor.entrySet()) {
            Vendor vendor = entry.getKey();
            for (VendorFeedRecord record : entry.getValue()) {
                Optional<String> internalKey = crosswalk.resolveToInternalKey(vendor.nativeScheme(), record.nativeIdentifier());
                if (internalKey.isEmpty()) {
                    continue; // not part of the known universe; see README limitations
                }
                byInternalKey.computeIfAbsent(internalKey.get(), k -> new EnumMap<>(Vendor.class)).put(vendor, record);
            }
        }

        List<GoldenSecurityRecord> golden = new ArrayList<>(byInternalKey.size());
        for (Map.Entry<String, Map<Vendor, VendorFeedRecord>> entry : byInternalKey.entrySet()) {
            golden.add(buildGoldenRecord(entry.getKey(), entry.getValue()));
        }
        return golden;
    }

    private GoldenSecurityRecord buildGoldenRecord(String internalKey, Map<Vendor, VendorFeedRecord> byVendor) {
        SecurityIdentifiers identifiers = crosswalk.identifiersFor(internalKey)
                .orElseThrow(() -> new IllegalStateException("crosswalk missing identifiers for " + internalKey));
        List<FieldResolution> resolutions = new ArrayList<>(GoldenField.values().length);
        for (GoldenField field : GoldenField.values()) {
            resolutions.add(FieldSurvivorshipResolver.resolve(field, byVendor, ruleSet));
        }
        return new GoldenSecurityRecord(internalKey, identifiers, resolutions);
    }
}
