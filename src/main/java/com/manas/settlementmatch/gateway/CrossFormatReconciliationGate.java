package com.manas.settlementmatch.gateway;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The real cross-format reconciliation gate: hash-indexed, buffers a trade
 * reference's arriving format records until all three
 * ({@link SourceFormat#FIX}, {@link SourceFormat#FPML},
 * {@link SourceFormat#DELIMITED}) are present, then compares every
 * canonical field across the three. Any disagreement quarantines the trade
 * reference (see {@link QuarantineResult}) instead of guessing which
 * format's value is correct; only a trade reference on which all three
 * formats agree on every field is reconciled and eligible to be handed to
 * the existing {@link com.manas.settlementmatch.engine.MatchingEngine} via
 * {@link NormalizedTradeRecordMapper}.
 *
 * <p>This is a new, ingestion-time layer, distinct from and upstream of the
 * existing two-legs-by-reference break matching: that logic still compares
 * PARTY_A against PARTY_B within a tolerance ruleset after a trade's
 * description has already been agreed across formats. This gate never
 * tolerances anything: a FIX allocation and its own FpML-style confirmation
 * are the same desk's two descriptions of the same fact, not two
 * counterparties' independent views, so any disagreement at all is a
 * data-quality problem, not a pricing discrepancy.
 *
 * <p>Deduplicates by {@code (sourceFormat, sourceMessageId)} at ingestion,
 * for the same reason {@link com.manas.settlementmatch.engine.MatchingEngine}
 * dedups by message id before touching its buffer: a redelivered FIX
 * message for a trade reference that has already completed reconciliation
 * must not be treated as a fresh, later-arriving fourth record.
 *
 * <p>Not thread-safe by contract with itself; {@code ingest} is synchronized.
 */
public final class CrossFormatReconciliationGate {

    private final Set<String> seenSourceMessages = new HashSet<>();
    private final Map<String, Map<SourceFormat, NormalizedTradeRecord>> pending = new HashMap<>();
    private final Map<String, ReconciledTrade> reconciledByTradeRef = new LinkedHashMap<>();
    private final Map<String, QuarantineResult> quarantinedByTradeRef = new LinkedHashMap<>();

    public synchronized GateOutcome ingest(NormalizedTradeRecord record) {
        String dedupKey = record.sourceFormat() + ":" + record.sourceMessageId();
        if (!seenSourceMessages.add(dedupKey)) {
            return GateOutcome.duplicate(record);
        }

        Map<SourceFormat, NormalizedTradeRecord> byFormat =
                pending.computeIfAbsent(record.tradeRef(), key -> new EnumMap<>(SourceFormat.class));
        byFormat.put(record.sourceFormat(), record);

        if (byFormat.size() < SourceFormat.values().length) {
            return GateOutcome.buffered(record);
        }

        pending.remove(record.tradeRef());
        NormalizedTradeRecord fix = byFormat.get(SourceFormat.FIX);
        NormalizedTradeRecord fpml = byFormat.get(SourceFormat.FPML);
        NormalizedTradeRecord delimited = byFormat.get(SourceFormat.DELIMITED);

        List<FieldDisagreement> disagreements = FieldComparator.compare(fix, fpml, delimited);
        if (disagreements.isEmpty()) {
            ReconciledTrade reconciled = new ReconciledTrade(record.tradeRef(), fix);
            reconciledByTradeRef.put(record.tradeRef(), reconciled);
            return GateOutcome.reconciled(reconciled);
        } else {
            QuarantineResult quarantine = new QuarantineResult(record.tradeRef(), disagreements, fix, fpml, delimited);
            quarantinedByTradeRef.put(record.tradeRef(), quarantine);
            return GateOutcome.quarantined(quarantine);
        }
    }

    public synchronized List<ReconciledTrade> reconciledTrades() {
        return new ArrayList<>(reconciledByTradeRef.values());
    }

    public synchronized List<QuarantineResult> quarantinedTrades() {
        return new ArrayList<>(quarantinedByTradeRef.values());
    }

    public synchronized int stillIncomplete() {
        return pending.size();
    }

    public synchronized int distinctSourceMessagesSeen() {
        return seenSourceMessages.size();
    }
}
