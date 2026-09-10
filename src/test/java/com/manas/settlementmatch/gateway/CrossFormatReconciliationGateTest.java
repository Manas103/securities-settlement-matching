package com.manas.settlementmatch.gateway;

import com.manas.settlementmatch.generator.CrossFormatTradeGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CrossFormatReconciliationGateTest {

    private CrossFormatReconciliationGate gate;

    @BeforeEach
    void setUp() {
        gate = new CrossFormatReconciliationGate();
    }

    private NormalizedTradeRecord record(SourceFormat format, String msgId, String tradeRef, String isin, long qty, String price, LocalDate date, String ccy, String account) {
        return new NormalizedTradeRecord(tradeRef, format, msgId, isin, qty, new BigDecimal(price), date, ccy, account);
    }

    @Test
    void firstTwoFormatsBufferUntilTheThirdArrives() {
        GateOutcome first = gate.ingest(record(SourceFormat.FIX, "f1", "TRD-1", "US1", 100, "10.00", LocalDate.of(2026, 7, 1), "USD", "ACC1"));
        assertThat(first.type()).isEqualTo(GateOutcome.Type.BUFFERED);

        GateOutcome second = gate.ingest(record(SourceFormat.FPML, "p1", "TRD-1", "US1", 100, "10.00", LocalDate.of(2026, 7, 1), "USD", "ACC1"));
        assertThat(second.type()).isEqualTo(GateOutcome.Type.BUFFERED);
        assertThat(gate.stillIncomplete()).isEqualTo(1);
    }

    @Test
    void allThreeFormatsAgreeingReconcile() {
        gate.ingest(record(SourceFormat.FIX, "f1", "TRD-1", "US1", 100, "10.00", LocalDate.of(2026, 7, 1), "USD", "ACC1"));
        gate.ingest(record(SourceFormat.FPML, "p1", "TRD-1", "US1", 100, "10.00", LocalDate.of(2026, 7, 1), "USD", "ACC1"));
        GateOutcome third = gate.ingest(record(SourceFormat.DELIMITED, "d1", "TRD-1", "US1", 100, "10.00", LocalDate.of(2026, 7, 1), "USD", "ACC1"));

        assertThat(third.type()).isEqualTo(GateOutcome.Type.RECONCILED);
        assertThat(gate.reconciledTrades()).hasSize(1);
        assertThat(gate.quarantinedTrades()).isEmpty();
        assertThat(gate.stillIncomplete()).isZero();
    }

    @Test
    void aPriceDisagreementBetweenFormatsIsQuarantinedNamingTheField() {
        gate.ingest(record(SourceFormat.FIX, "f1", "TRD-1", "US1", 100, "10.00", LocalDate.of(2026, 7, 1), "USD", "ACC1"));
        gate.ingest(record(SourceFormat.FPML, "p1", "TRD-1", "US1", 100, "10.50", LocalDate.of(2026, 7, 1), "USD", "ACC1"));
        GateOutcome third = gate.ingest(record(SourceFormat.DELIMITED, "d1", "TRD-1", "US1", 100, "10.00", LocalDate.of(2026, 7, 1), "USD", "ACC1"));

        assertThat(third.type()).isEqualTo(GateOutcome.Type.QUARANTINED);
        QuarantineResult result = third.quarantineResult();
        assertThat(result.fieldNames()).isEqualTo("price");
        assertThat(result.disagreements()).hasSize(1);
        FieldDisagreement disagreement = result.disagreements().get(0);
        assertThat(disagreement.fixValue()).isEqualTo("10");
        assertThat(disagreement.fpmlValue()).isEqualTo("10.5");
        assertThat(disagreement.delimitedValue()).isEqualTo("10");
        assertThat(gate.quarantinedTrades()).hasSize(1);
        assertThat(gate.reconciledTrades()).isEmpty();
    }

    @Test
    void disagreementIsNotMaskedByPriceScaleDifferencesAlone() {
        // 10.00 and 10.0 and 10 are the same numeric value at different scales; this must reconcile,
        // not quarantine on a formatting artifact.
        gate.ingest(record(SourceFormat.FIX, "f1", "TRD-1", "US1", 100, "10.00", LocalDate.of(2026, 7, 1), "USD", "ACC1"));
        gate.ingest(record(SourceFormat.FPML, "p1", "TRD-1", "US1", 100, "10.0", LocalDate.of(2026, 7, 1), "USD", "ACC1"));
        GateOutcome third = gate.ingest(record(SourceFormat.DELIMITED, "d1", "TRD-1", "US1", 100, "10", LocalDate.of(2026, 7, 1), "USD", "ACC1"));

        assertThat(third.type()).isEqualTo(GateOutcome.Type.RECONCILED);
    }

    @Test
    void duplicateSourceMessageIsIgnoredEvenBeforeReconciliationCompletes() {
        gate.ingest(record(SourceFormat.FIX, "f1", "TRD-1", "US1", 100, "10.00", LocalDate.of(2026, 7, 1), "USD", "ACC1"));
        GateOutcome duplicate = gate.ingest(record(SourceFormat.FIX, "f1", "TRD-1", "US1", 100, "10.00", LocalDate.of(2026, 7, 1), "USD", "ACC1"));

        assertThat(duplicate.type()).isEqualTo(GateOutcome.Type.DUPLICATE);
        assertThat(gate.stillIncomplete()).isEqualTo(1);
    }

    @Test
    void aTradeArrivingAsFixFpmlAndDelimitedNormalizesIntoOneCanonicalRecord() {
        // Claim 1: the same trade, genuinely parsed from three disagreeing wire formats, normalizes
        // into one canonical record when all three agree.
        CrossFormatTradeGenerator generator = new CrossFormatTradeGenerator();
        var stream = generator.generate(1, 0, 555L);
        List<NormalizedTradeRecord> parsed = CrossFormatTestSupport.parseOneTrade(stream.trades().get(0));

        assertThat(parsed).extracting(NormalizedTradeRecord::sourceFormat)
                .containsExactlyInAnyOrder(SourceFormat.FIX, SourceFormat.FPML, SourceFormat.DELIMITED);

        GateOutcome outcome = null;
        for (NormalizedTradeRecord record : parsed) {
            outcome = gate.ingest(record);
        }

        assertThat(outcome).isNotNull();
        assertThat(outcome.type()).isEqualTo(GateOutcome.Type.RECONCILED);
        NormalizedTradeRecord canonical = outcome.reconciledTrade().canonical();
        for (NormalizedTradeRecord record : parsed) {
            assertThat(record.tradeRef()).isEqualTo(canonical.tradeRef());
            assertThat(record.isin()).isEqualTo(canonical.isin());
            assertThat(record.quantity()).isEqualTo(canonical.quantity());
            assertThat(record.price()).isEqualByComparingTo(canonical.price());
            assertThat(record.settlementDate()).isEqualTo(canonical.settlementDate());
            assertThat(record.currency()).isEqualTo(canonical.currency());
            assertThat(record.account()).isEqualTo(canonical.account());
        }
    }
}
