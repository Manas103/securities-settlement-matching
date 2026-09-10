package com.manas.settlementmatch.gateway;

import java.util.ArrayList;
import java.util.List;

/**
 * A deliberately slow, deliberately unindexed cross-format reconciliation
 * oracle, diffed exactly against the real
 * {@link CrossFormatReconciliationGate} per BUILDER.md section 2 ("a
 * reference oracle where correctness is non-obvious"). Everything here is a
 * plain list scan: {@code ingest} just appends to a list, and
 * {@link #reconcileAll()} finds each trade reference's records with nested
 * loops over that list, no {@code Map}-based grouping, no incremental
 * state, nothing that could hide the same indexing bug the real gate might
 * have.
 *
 * <p>Operates on a static, already-fully-received list rather than a
 * stream, which is a correct model of the same question the matching
 * engine's oracle answers: does an out-of-order, duplicated delivery of the
 * three formats produce the same reconciled/quarantined outcome as
 * computing it directly over the deduplicated set. Deliberately O(n^2);
 * only run over small samples in tests.
 */
public final class ReferenceOracleReconciliationGate {

    private final List<NormalizedTradeRecord> allRecords = new ArrayList<>();

    public void ingest(NormalizedTradeRecord record) {
        allRecords.add(record);
    }

    public record OracleResult(List<ReconciledTrade> reconciled, List<QuarantineResult> quarantined, List<String> incompleteTradeRefs) {
    }

    public OracleResult reconcileAll() {
        // Step 1: dedup by (sourceFormat, sourceMessageId), keeping only the first occurrence,
        // via a plain linear scan (no HashSet).
        List<NormalizedTradeRecord> deduped = new ArrayList<>();
        List<String> seenKeys = new ArrayList<>();
        for (NormalizedTradeRecord record : allRecords) {
            String key = record.sourceFormat() + ":" + record.sourceMessageId();
            if (!seenKeys.contains(key)) {
                seenKeys.add(key);
                deduped.add(record);
            }
        }

        // Step 2: collect distinct trade references, in first-seen order, via a plain linear scan.
        List<String> tradeRefs = new ArrayList<>();
        for (NormalizedTradeRecord record : deduped) {
            if (!tradeRefs.contains(record.tradeRef())) {
                tradeRefs.add(record.tradeRef());
            }
        }

        List<ReconciledTrade> reconciled = new ArrayList<>();
        List<QuarantineResult> quarantined = new ArrayList<>();
        List<String> incomplete = new ArrayList<>();

        // Step 3: for each trade reference, scan the whole deduped list three times to find one
        // record per format (a real oracle would just do one scan per format; three separate scans
        // keep this deliberately naive and independent of any format-indexing cleverness).
        for (String tradeRef : tradeRefs) {
            NormalizedTradeRecord fix = findOne(deduped, tradeRef, SourceFormat.FIX);
            NormalizedTradeRecord fpml = findOne(deduped, tradeRef, SourceFormat.FPML);
            NormalizedTradeRecord delimited = findOne(deduped, tradeRef, SourceFormat.DELIMITED);

            if (fix == null || fpml == null || delimited == null) {
                incomplete.add(tradeRef);
                continue;
            }

            List<FieldDisagreement> disagreements = FieldComparator.compare(fix, fpml, delimited);
            if (disagreements.isEmpty()) {
                reconciled.add(new ReconciledTrade(tradeRef, fix));
            } else {
                quarantined.add(new QuarantineResult(tradeRef, disagreements, fix, fpml, delimited));
            }
        }

        return new OracleResult(reconciled, quarantined, incomplete);
    }

    private NormalizedTradeRecord findOne(List<NormalizedTradeRecord> deduped, String tradeRef, SourceFormat format) {
        NormalizedTradeRecord found = null;
        for (NormalizedTradeRecord record : deduped) {
            if (record.tradeRef().equals(tradeRef) && record.sourceFormat() == format) {
                found = record;
            }
        }
        return found;
    }
}
