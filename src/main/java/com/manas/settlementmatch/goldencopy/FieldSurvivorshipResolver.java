package com.manas.settlementmatch.goldencopy;

import java.util.List;
import java.util.Map;

/**
 * The one place that knows how to resolve a single golden field from the
 * vendor records present for a security, given its declared survivorship
 * rule. Shared, byte-for-byte, between {@link GoldenCopyConsolidationEngine}
 * (the fast, hash-indexed path) and {@link GoldenCopyReferenceOracle} (the
 * deliberately slow oracle), the same discipline the cross-format
 * gateway's {@code FieldComparator} already follows in this repository: a
 * divergence between the fast engine and the oracle can only come from how
 * they group vendor records into one security, never from the two paths
 * disagreeing about what resolving a field means.
 */
final class FieldSurvivorshipResolver {

    private FieldSurvivorshipResolver() {
    }

    static FieldResolution resolve(GoldenField field, Map<Vendor, VendorFeedRecord> byVendor, SurvivorshipRuleSet ruleSet) {
        SurvivorshipRule rule = ruleSet.ruleFor(field);
        List<Vendor> precedence = rule.precedence();
        if (precedence.isEmpty()) {
            return resolveNoRule(field, byVendor, rule.dataOwner());
        }
        return resolveByPrecedenceChain(field, byVendor, precedence, rule.dataOwner());
    }

    private static FieldResolution resolveByPrecedenceChain(GoldenField field, Map<Vendor, VendorFeedRecord> byVendor,
                                                             List<Vendor> precedence, String dataOwner) {
        for (Vendor vendor : precedence) {
            VendorFeedRecord record = byVendor.get(vendor);
            if (record == null) {
                continue;
            }
            Object value = field.valueFrom(record);
            if (value != null) {
                return new FieldResolution(field, FieldOutcome.RESOLVED, value, vendor, record.vendorRecordId(), null);
            }
        }
        // Every vendor the declared precedence chain names is either absent for this security or
        // null for this field: nothing left to fall back to. Held, not guessed.
        return new FieldResolution(field, FieldOutcome.HELD, null, null, null, dataOwner);
    }

    private static FieldResolution resolveNoRule(GoldenField field, Map<Vendor, VendorFeedRecord> byVendor, String dataOwner) {
        Object consensusValue = null;
        Vendor consensusVendor = null;
        String consensusRecordId = null;
        boolean disagreement = false;

        for (Vendor vendor : Vendor.values()) {
            VendorFeedRecord record = byVendor.get(vendor);
            if (record == null) {
                continue;
            }
            Object value = field.valueFrom(record);
            if (value == null) {
                continue;
            }
            if (consensusValue == null) {
                consensusValue = value;
                consensusVendor = vendor;
                consensusRecordId = record.vendorRecordId();
            } else if (!consensusValue.equals(value)) {
                disagreement = true;
            }
        }

        if (consensusValue == null || disagreement) {
            // No declared precedence exists for this field at all: either no vendor carried a
            // value, or at least two vendors disagree with no rule to break the tie. Held for the
            // named owner either way, never guessed.
            return new FieldResolution(field, FieldOutcome.HELD, null, null, null, dataOwner);
        }
        return new FieldResolution(field, FieldOutcome.RESOLVED, consensusValue, consensusVendor, consensusRecordId, null);
    }
}
