package com.manas.settlementmatch.gateway;

import com.manas.settlementmatch.generator.CrossFormatTradeGenerator;
import com.manas.settlementmatch.generator.NoisyStreamBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Diffs the real, hash-indexed {@link CrossFormatReconciliationGate}
 * against the deliberately slow, deliberately unindexed
 * {@link ReferenceOracleReconciliationGate} over the same synthetic
 * cross-format dataset, with a noisy (duplicated, shuffled) delivery order
 * for the real gate. Mirrors {@code engine.ReferenceOracleDiffTest}'s
 * structure exactly, per BUILDER.md section 2.
 */
class ReferenceOracleReconciliationGateDiffTest {

    @Test
    void realGateAgreesWithTheReferenceOracleOverNoisyDelivery() {
        CrossFormatTradeGenerator generator = new CrossFormatTradeGenerator();
        var stream = generator.generate(300, 30, 4242L);
        List<NormalizedTradeRecord> canonicalRecords = CrossFormatTestSupport.parseAll(stream.trades());

        // The oracle judges the canonical, already-complete dataset directly.
        ReferenceOracleReconciliationGate oracle = new ReferenceOracleReconciliationGate();
        canonicalRecords.forEach(oracle::ingest);
        ReferenceOracleReconciliationGate.OracleResult oracleResult = oracle.reconcileAll();

        // The real gate consumes a noisy (5% duplicated, fully shuffled) delivery of the same records.
        List<NormalizedTradeRecord> noisy = NoisyStreamBuilder.buildNoisyStream(canonicalRecords, 0.05, 777L);
        CrossFormatReconciliationGate gate = new CrossFormatReconciliationGate();
        for (NormalizedTradeRecord record : noisy) {
            gate.ingest(record);
        }

        Set<String> oracleReconciledRefs = oracleResult.reconciled().stream().map(ReconciledTrade::tradeRef).collect(Collectors.toSet());
        Set<String> gateReconciledRefs = gate.reconciledTrades().stream().map(ReconciledTrade::tradeRef).collect(Collectors.toSet());
        assertThat(gateReconciledRefs).isEqualTo(oracleReconciledRefs);

        Set<String> oracleQuarantinedRefs = oracleResult.quarantined().stream().map(QuarantineResult::tradeRef).collect(Collectors.toSet());
        Set<String> gateQuarantinedRefs = gate.quarantinedTrades().stream().map(QuarantineResult::tradeRef).collect(Collectors.toSet());
        assertThat(gateQuarantinedRefs).isEqualTo(oracleQuarantinedRefs);

        for (QuarantineResult oracleQuarantine : oracleResult.quarantined()) {
            QuarantineResult gateQuarantine = gate.quarantinedTrades().stream()
                    .filter(q -> q.tradeRef().equals(oracleQuarantine.tradeRef()))
                    .findFirst()
                    .orElseThrow();
            assertThat(gateQuarantine.fieldNames()).isEqualTo(oracleQuarantine.fieldNames());
        }

        assertThat(oracleResult.incompleteTradeRefs()).isEmpty();
        assertThat(gate.stillIncomplete()).isZero();
        assertThat(oracleResult.quarantined()).hasSize(30);
        assertThat(gate.reconciledTrades()).hasSize(300 - 30);
    }
}
