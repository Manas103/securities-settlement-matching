package com.manas.settlementmatch.engine;

import com.manas.settlementmatch.model.SettlementInstruction;
import com.manas.settlementmatch.model.Side;
import com.manas.settlementmatch.tolerance.ToleranceRuleSet;

import java.util.ArrayList;
import java.util.List;

/**
 * A deliberately slow, deliberately unindexed matcher used only to check the
 * real {@link MatchingEngine} against, per the reference-oracle requirement
 * (BUILDER.md section 2, "reference oracle where correctness is
 * non-obvious"). Everything here is a plain list scan: no HashMap, no
 * incremental state, nothing that could hide the same bug the real engine
 * might have. It operates on a static, already-fully-received list rather
 * than a stream, which is a correct model of the problem specifically
 * because settlement matching's correctness question is not about streaming
 * mechanics, it is about "does out-of-order arrival and duplicate delivery
 * produce the same final matches and breaks as if everything had arrived in
 * one neat, already-deduplicated, already-paired batch" -- which is exactly
 * what this class computes directly.
 *
 * <p>Deliberately O(n^2) (a linear scan for dedup, a linear scan per trade
 * reference to find its two sides); only ever run over small samples in
 * tests, never over the full 100,000-message benchmark set.
 */
public final class ReferenceOracleMatcher {

    private final ToleranceRuleSet ruleSet;

    public ReferenceOracleMatcher(ToleranceRuleSet ruleSet) {
        this.ruleSet = ruleSet;
    }

    public record OracleResult(List<MatchedPositionResult> matched, List<BreakResult> breaks) {
    }

    public OracleResult match(List<SettlementInstruction> allInstructions) {
        // Step 1: dedup by message id, keeping only the first occurrence, via a plain linear scan.
        List<SettlementInstruction> deduped = new ArrayList<>();
        List<String> seenIds = new ArrayList<>();
        for (SettlementInstruction instruction : allInstructions) {
            if (!seenIds.contains(instruction.messageId())) {
                seenIds.add(instruction.messageId());
                deduped.add(instruction);
            }
        }

        // Step 2: collect the distinct trade references, in first-seen order, via a plain linear scan.
        List<String> tradeRefs = new ArrayList<>();
        for (SettlementInstruction instruction : deduped) {
            if (!tradeRefs.contains(instruction.tradeRef())) {
                tradeRefs.add(instruction.tradeRef());
            }
        }

        List<MatchedPositionResult> matched = new ArrayList<>();
        List<BreakResult> breaks = new ArrayList<>();

        // Step 3: for each trade reference, scan the whole deduped list twice to find its two sides.
        for (String tradeRef : tradeRefs) {
            SettlementInstruction partyA = null;
            SettlementInstruction partyB = null;
            for (SettlementInstruction instruction : deduped) {
                if (instruction.tradeRef().equals(tradeRef) && instruction.side() == Side.PARTY_A) {
                    partyA = instruction;
                }
                if (instruction.tradeRef().equals(tradeRef) && instruction.side() == Side.PARTY_B) {
                    partyB = instruction;
                }
            }
            if (partyA == null || partyB == null) {
                // Incomplete pair in this dataset; the oracle only judges complete pairs,
                // same as the real engine only judges pairs once both sides have arrived.
                continue;
            }
            var discrepancies = ruleSet.compare(partyA, partyB);
            if (discrepancies.isEmpty()) {
                matched.add(new MatchedPositionResult(tradeRef, partyA, partyB));
            } else {
                breaks.add(new BreakResult(tradeRef, partyA, partyB, discrepancies));
            }
        }

        return new OracleResult(matched, breaks);
    }
}
