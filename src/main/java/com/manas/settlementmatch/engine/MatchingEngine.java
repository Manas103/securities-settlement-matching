package com.manas.settlementmatch.engine;

import com.manas.settlementmatch.model.SettlementInstruction;
import com.manas.settlementmatch.model.Side;
import com.manas.settlementmatch.tolerance.ToleranceRuleSet;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The real matcher: hash-indexed, buffers by trade reference so it matches
 * correctly regardless of which side arrives first, and dedups by message id
 * at ingestion before a message is allowed anywhere near the buffer.
 *
 * <p><b>Why dedup at ingestion, not after matching.</b> If dedup happened
 * after a match/break decision, a redelivered second-side message would
 * arrive at an engine that has already consumed and discarded its
 * counterpart from the awaiting buffer, and would be treated as the *first*
 * side of a brand new pairing, silently waiting forever (or worse, matching
 * against a genuinely different later trade that happens to reuse the slot).
 * Rejecting the duplicate by message id before it can touch the buffer at
 * all means a duplicate is provably a no-op: it cannot create a phantom
 * match, a phantom break, or a phantom buffered-and-waiting entry.
 *
 * <p><b>Why buffer by trade reference instead of assuming ordered
 * PARTY_A-then-PARTY_B delivery.</b> Kafka guarantees ordering only within a
 * partition, and even within a partition nothing requires the producer to
 * emit both legs adjacently. A design that assumed order would need to
 * reorder upstream (an unbounded, latency-costly buffer) or would misfire on
 * real traffic. Buffering per trade reference and completing on "second side
 * seen" is the same amount of state either way, but works for any arrival
 * order for free.
 *
 * <p>Not thread-safe by contract with itself; the single {@code ingest} entry
 * point is synchronized so concurrent Kafka listener container threads (if
 * concurrency is ever turned up in {@code application.yml}) cannot interleave
 * a check-then-act on the buffer.
 */
public final class MatchingEngine {

    private final ToleranceRuleSet ruleSet;

    private final Set<String> seenMessageIds = new HashSet<>();
    private final Map<String, SettlementInstruction> awaitingCounterpart = new HashMap<>();
    private final Map<String, MatchedPositionResult> matchedByTradeRef = new LinkedHashMap<>();
    private final Map<String, BreakResult> breaksByTradeRef = new LinkedHashMap<>();

    public MatchingEngine(ToleranceRuleSet ruleSet) {
        this.ruleSet = ruleSet;
    }

    public synchronized MatchOutcome ingest(SettlementInstruction instruction) {
        if (!seenMessageIds.add(instruction.messageId())) {
            return MatchOutcome.duplicate(instruction);
        }

        SettlementInstruction counterpart = awaitingCounterpart.remove(instruction.tradeRef());
        if (counterpart == null) {
            awaitingCounterpart.put(instruction.tradeRef(), instruction);
            return MatchOutcome.buffered(instruction);
        }

        SettlementInstruction partyA = counterpart.side() == Side.PARTY_A ? counterpart : instruction;
        SettlementInstruction partyB = counterpart.side() == Side.PARTY_A ? instruction : counterpart;

        List<com.manas.settlementmatch.tolerance.FieldDiscrepancy> discrepancies = ruleSet.compare(partyA, partyB);
        if (discrepancies.isEmpty()) {
            MatchedPositionResult position = new MatchedPositionResult(instruction.tradeRef(), partyA, partyB);
            matchedByTradeRef.put(instruction.tradeRef(), position);
            return MatchOutcome.matched(position);
        } else {
            BreakResult breakResult = new BreakResult(instruction.tradeRef(), partyA, partyB, discrepancies);
            breaksByTradeRef.put(instruction.tradeRef(), breakResult);
            return MatchOutcome.breakFiled(breakResult);
        }
    }

    public synchronized List<MatchedPositionResult> matchedPositions() {
        return new ArrayList<>(matchedByTradeRef.values());
    }

    public synchronized List<BreakResult> breaks() {
        return new ArrayList<>(breaksByTradeRef.values());
    }

    public synchronized int stillAwaitingCounterpart() {
        return awaitingCounterpart.size();
    }

    public synchronized int distinctMessageIdsSeen() {
        return seenMessageIds.size();
    }
}
