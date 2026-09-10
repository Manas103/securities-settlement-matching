package com.manas.settlementmatch.bench;

import com.manas.settlementmatch.gateway.CrossFormatReconciliationGate;
import com.manas.settlementmatch.gateway.DelimitedPostTradeFileParser;
import com.manas.settlementmatch.gateway.FixAllocationMessageParser;
import com.manas.settlementmatch.gateway.FpmlConfirmationParser;
import com.manas.settlementmatch.gateway.GateOutcome;
import com.manas.settlementmatch.gateway.NormalizedTradeRecord;
import com.manas.settlementmatch.gateway.QuarantineResult;
import com.manas.settlementmatch.generator.CrossFormatTradeGenerator;
import com.manas.settlementmatch.generator.NoisyStreamBuilder;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The real measurement run behind the multi-protocol gateway's claims:
 * normalizing one trade's three formats into one canonical record, 30 of
 * 30 seeded cross-format disagreements quarantined naming the correct
 * field, and the false-hold rate over a clean (all-three-agree) sample.
 * Tagged {@code benchmark}, excluded from the default {@code mvn test} run
 * for the same reason {@link BenchmarkRunner} is: run explicitly with
 * {@code mvn test -Dtest=CrossFormatBenchmarkRunner -Dsurefire.excludedGroups=}.
 */
class CrossFormatBenchmarkRunner {

    private static final int QUARANTINE_TRADE_COUNT = 30;
    private static final int QUARANTINE_SEEDED_COUNT = 30;
    private static final long QUARANTINE_GENERATOR_SEED = 20260901L;
    private static final long QUARANTINE_NOISE_SEED = 314159L;

    private static final int CLEAN_TRADE_COUNT = 200;
    private static final long CLEAN_GENERATOR_SEED = 20260902L;
    private static final long CLEAN_NOISE_SEED = 271828L;

    private final FixAllocationMessageParser fixParser = new FixAllocationMessageParser();
    private final FpmlConfirmationParser fpmlParser = new FpmlConfirmationParser();
    private final DelimitedPostTradeFileParser delimitedParser = new DelimitedPostTradeFileParser();

    @Test
    @Tag("benchmark")
    void measureCrossFormatGatewayClaims() {
        StringBuilder report = new StringBuilder();
        line(report, "=== Multi-Protocol Gateway -- cross-format reconciliation benchmark run ===");
        line(report, "started: " + Instant.now());

        // -- claim: one trade arriving as FIX + FpML-style + delimited normalizes into one canonical record --
        CrossFormatTradeGenerator generator = new CrossFormatTradeGenerator();
        var demoStream = generator.generate(1, 0, 99L);
        List<NormalizedTradeRecord> demoRecords = parseAll(demoStream.trades());
        CrossFormatReconciliationGate demoGate = new CrossFormatReconciliationGate();
        GateOutcome demoOutcome = null;
        for (NormalizedTradeRecord record : demoRecords) {
            demoOutcome = demoGate.ingest(record);
        }
        boolean normalizedIntoOneCanonicalRecord = demoOutcome != null && demoOutcome.type() == GateOutcome.Type.RECONCILED;
        line(report, "");
        line(report, "-- claim: FIX + FpML-style + delimited normalize into one canonical record --");
        line(report, "result: " + normalizedIntoOneCanonicalRecord);

        // -- claim: 30 of 30 seeded cross-format disagreements quarantined --
        var quarantineStream = generator.generate(QUARANTINE_TRADE_COUNT, QUARANTINE_SEEDED_COUNT, QUARANTINE_GENERATOR_SEED);
        List<NormalizedTradeRecord> quarantineRecords = parseAll(quarantineStream.trades());
        List<NormalizedTradeRecord> noisyQuarantineRecords = NoisyStreamBuilder.buildNoisyStream(quarantineRecords, 0.05, QUARANTINE_NOISE_SEED);
        CrossFormatReconciliationGate quarantineGate = new CrossFormatReconciliationGate();
        for (NormalizedTradeRecord record : noisyQuarantineRecords) {
            quarantineGate.ingest(record);
        }

        Set<String> seededRefs = new HashSet<>(quarantineStream.seededDisagreementTradeRefs());
        Set<String> quarantinedRefs = quarantineGate.quarantinedTrades().stream()
                .map(QuarantineResult::tradeRef).collect(Collectors.toSet());
        long quarantinedCorrectly = seededRefs.stream().filter(quarantinedRefs::contains).count();

        long namedCorrectField = 0;
        for (QuarantineResult result : quarantineGate.quarantinedTrades()) {
            if (!seededRefs.contains(result.tradeRef())) {
                continue;
            }
            String expectedField = quarantineStream.seededFieldByTradeRef().get(result.tradeRef()).canonicalFieldName();
            if (result.fieldNames().equals(expectedField)) {
                namedCorrectField++;
            }
        }

        line(report, "");
        line(report, "-- claim: 30 of 30 cross-format disagreements quarantined rather than guessed --");
        line(report, "seeded disagreements: " + QUARANTINE_SEEDED_COUNT);
        line(report, "trades in this sample: " + QUARANTINE_TRADE_COUNT + " (all seeded; every trade in this batch has a disagreement)");
        line(report, "quarantined (of the seeded set): " + quarantinedCorrectly + " / " + QUARANTINE_SEEDED_COUNT);
        line(report, "quarantined naming exactly the seeded disagreeing field: " + namedCorrectField + " / " + QUARANTINE_SEEDED_COUNT);
        line(report, "total quarantined by the gate: " + quarantineGate.quarantinedTrades().size());
        line(report, "total reconciled by the gate: " + quarantineGate.reconciledTrades().size());
        line(report, "still incomplete at end of run: " + quarantineGate.stillIncomplete());

        // -- companion claim: false-hold rate over a clean (all-three-agree) sample --
        var cleanStream = generator.generate(CLEAN_TRADE_COUNT, 0, CLEAN_GENERATOR_SEED);
        List<NormalizedTradeRecord> cleanRecords = parseAll(cleanStream.trades());
        List<NormalizedTradeRecord> noisyCleanRecords = NoisyStreamBuilder.buildNoisyStream(cleanRecords, 0.05, CLEAN_NOISE_SEED);
        CrossFormatReconciliationGate cleanGate = new CrossFormatReconciliationGate();
        for (NormalizedTradeRecord record : noisyCleanRecords) {
            cleanGate.ingest(record);
        }
        long falselyQuarantined = cleanGate.quarantinedTrades().size();
        double falseHoldRate = (double) falselyQuarantined / CLEAN_TRADE_COUNT;

        line(report, "");
        line(report, "-- companion claim: cross_format_false_hold_rate (clean trades falsely quarantined) --");
        line(report, "clean trades in this sample (all three formats agree on every field): " + CLEAN_TRADE_COUNT);
        line(report, "falsely quarantined: " + falselyQuarantined + " / " + CLEAN_TRADE_COUNT);
        line(report, "false-hold rate: " + falseHoldRate);
        line(report, "reconciled: " + cleanGate.reconciledTrades().size() + " / " + CLEAN_TRADE_COUNT);
        line(report, "still incomplete at end of run: " + cleanGate.stillIncomplete());

        line(report, "");
        line(report, "finished: " + Instant.now());
        System.out.println(report);

        assertThat(normalizedIntoOneCanonicalRecord).isTrue();
        assertThat(quarantineGate.stillIncomplete()).isZero();
        assertThat(cleanGate.stillIncomplete()).isZero();
    }

    private List<NormalizedTradeRecord> parseAll(List<CrossFormatTradeGenerator.RawTradeMessages> trades) {
        List<NormalizedTradeRecord> out = new ArrayList<>(trades.size() * 3);
        for (CrossFormatTradeGenerator.RawTradeMessages raw : trades) {
            out.add(fixParser.parse(raw.fixMessage()));
            out.add(fpmlParser.parse(raw.fpmlXml()));
            out.add(delimitedParser.parse(raw.delimitedLine()));
        }
        return out;
    }

    private void line(StringBuilder sb, String text) {
        sb.append(text).append(System.lineSeparator());
    }
}
