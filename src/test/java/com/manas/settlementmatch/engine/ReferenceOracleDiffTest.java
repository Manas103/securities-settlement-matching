package com.manas.settlementmatch.engine;

import com.manas.settlementmatch.generator.NoisyStreamBuilder;
import com.manas.settlementmatch.generator.SyntheticInstructionGenerator;
import com.manas.settlementmatch.model.SettlementInstruction;
import com.manas.settlementmatch.tolerance.ToleranceRuleSet;
import com.manas.settlementmatch.tolerance.ToleranceRuleSetLoader;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Diffs the real, hash-indexed {@link MatchingEngine} against the
 * deliberately slow, deliberately unindexed {@link ReferenceOracleMatcher}
 * over the same synthetic dataset, including a noisy (duplicated,
 * shuffled) delivery order for the real engine. BUILDER.md section 2:
 * "a reference oracle where correctness is non-obvious".
 *
 * Kept to a few thousand messages, not the full 100,000, because the
 * oracle is deliberately O(n^2).
 */
class ReferenceOracleDiffTest {

    private final ToleranceRuleSet ruleSet = ToleranceRuleSetLoader.loadFromClasspath("tolerance-rules.yml");

    @Test
    void realEngineAgreesWithTheReferenceOracleOverNoisyDelivery() {
        SyntheticInstructionGenerator generator = new SyntheticInstructionGenerator();
        var stream = generator.generate(1500, 30, 42L);

        // The oracle judges the canonical, already-complete dataset directly.
        ReferenceOracleMatcher oracle = new ReferenceOracleMatcher(ruleSet);
        ReferenceOracleMatcher.OracleResult oracleResult = oracle.match(stream.instructions());

        // The real engine consumes a noisy (5% duplicated, fully shuffled) delivery of the same dataset.
        List<SettlementInstruction> noisy = NoisyStreamBuilder.buildNoisyStream(stream.instructions(), 0.05, 99L);
        MatchingEngine engine = new MatchingEngine(ruleSet);
        for (SettlementInstruction instruction : noisy) {
            engine.ingest(instruction);
        }

        Set<String> oracleMatchedRefs = oracleResult.matched().stream().map(MatchedPositionResult::tradeRef).collect(Collectors.toSet());
        Set<String> engineMatchedRefs = engine.matchedPositions().stream().map(MatchedPositionResult::tradeRef).collect(Collectors.toSet());
        assertThat(engineMatchedRefs).isEqualTo(oracleMatchedRefs);

        Set<String> oracleBreakRefs = oracleResult.breaks().stream().map(BreakResult::tradeRef).collect(Collectors.toSet());
        Set<String> engineBreakRefs = engine.breaks().stream().map(BreakResult::tradeRef).collect(Collectors.toSet());
        assertThat(engineBreakRefs).isEqualTo(oracleBreakRefs);

        // Every seeded break must be a break in both the oracle and the real engine, naming the same fields.
        for (BreakResult oracleBreak : oracleResult.breaks()) {
            BreakResult engineBreak = engine.breaks().stream()
                    .filter(b -> b.tradeRef().equals(oracleBreak.tradeRef()))
                    .findFirst()
                    .orElseThrow();
            assertThat(engineBreak.fieldNames()).isEqualTo(oracleBreak.fieldNames());
        }

        assertThat(oracleResult.breaks()).hasSize(30);
        assertThat(engine.matchedPositions()).hasSize(1500 - 30);
    }
}
