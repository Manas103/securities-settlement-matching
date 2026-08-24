package com.manas.settlementmatch.engine;

import com.manas.settlementmatch.model.Side;
import com.manas.settlementmatch.model.SettlementInstruction;
import com.manas.settlementmatch.tolerance.ToleranceRuleSetLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class MatchingEngineTest {

    private MatchingEngine engine;

    @BeforeEach
    void setUp() {
        engine = new MatchingEngine(ToleranceRuleSetLoader.loadFromClasspath("tolerance-rules.yml"));
    }

    private SettlementInstruction instruction(String msgId, String tradeRef, Side side, String price) {
        return new SettlementInstruction(msgId, tradeRef, side, "US0378331005", 1000, new BigDecimal(price),
                LocalDate.of(2026, 7, 10), "USD", "CPTY", 0L);
    }

    @Test
    void firstSideBuffersUntilSecondSideArrives() {
        MatchOutcome first = engine.ingest(instruction("m1", "TRD-1", Side.PARTY_A, "100.00"));
        assertThat(first.type()).isEqualTo(MatchOutcome.Type.BUFFERED);
        assertThat(engine.stillAwaitingCounterpart()).isEqualTo(1);
        assertThat(engine.matchedPositions()).isEmpty();
    }

    @Test
    void bothSidesAgreeingProduceAMatchRegardlessOfArrivalOrder() {
        engine.ingest(instruction("m1", "TRD-1", Side.PARTY_B, "100.00")); // PARTY_B arrives first
        MatchOutcome second = engine.ingest(instruction("m2", "TRD-1", Side.PARTY_A, "100.00"));

        assertThat(second.type()).isEqualTo(MatchOutcome.Type.MATCHED);
        assertThat(engine.matchedPositions()).hasSize(1);
        assertThat(engine.stillAwaitingCounterpart()).isZero();
        assertThat(engine.matchedPositions().get(0).partyA().side()).isEqualTo(Side.PARTY_A);
        assertThat(engine.matchedPositions().get(0).partyB().side()).isEqualTo(Side.PARTY_B);
    }

    @Test
    void disagreeingSidesFileABreakNamingTheField() {
        engine.ingest(instruction("m1", "TRD-1", Side.PARTY_A, "100.00"));
        MatchOutcome second = engine.ingest(instruction("m2", "TRD-1", Side.PARTY_B, "150.00"));

        assertThat(second.type()).isEqualTo(MatchOutcome.Type.BREAK);
        assertThat(engine.breaks()).hasSize(1);
        assertThat(engine.breaks().get(0).fieldNames()).isEqualTo("price");
    }

    @Test
    void duplicateMessageIdIsIgnoredEvenBeforeAMatchCompletes() {
        engine.ingest(instruction("m1", "TRD-1", Side.PARTY_A, "100.00"));
        MatchOutcome duplicate = engine.ingest(instruction("m1", "TRD-1", Side.PARTY_A, "100.00"));

        assertThat(duplicate.type()).isEqualTo(MatchOutcome.Type.DUPLICATE);
        assertThat(engine.stillAwaitingCounterpart()).isEqualTo(1);
    }

    @Test
    void duplicateOfTheSecondSideDoesNotCreateAPhantomSecondMatch() {
        engine.ingest(instruction("m1", "TRD-1", Side.PARTY_A, "100.00"));
        MatchOutcome matched = engine.ingest(instruction("m2", "TRD-1", Side.PARTY_B, "100.00"));
        assertThat(matched.type()).isEqualTo(MatchOutcome.Type.MATCHED);

        // A redelivery of the exact same second-side message (same messageId) must be a no-op,
        // not a fresh "buffered, awaiting a third leg" state and not a second match.
        MatchOutcome redelivered = engine.ingest(instruction("m2", "TRD-1", Side.PARTY_B, "100.00"));

        assertThat(redelivered.type()).isEqualTo(MatchOutcome.Type.DUPLICATE);
        assertThat(engine.matchedPositions()).hasSize(1);
        assertThat(engine.stillAwaitingCounterpart()).isZero();
    }

    @Test
    void duplicateAfterABreakDoesNotCreateAPhantomSecondBreak() {
        engine.ingest(instruction("m1", "TRD-1", Side.PARTY_A, "100.00"));
        engine.ingest(instruction("m2", "TRD-1", Side.PARTY_B, "150.00"));

        MatchOutcome redelivered = engine.ingest(instruction("m1", "TRD-1", Side.PARTY_A, "100.00"));

        assertThat(redelivered.type()).isEqualTo(MatchOutcome.Type.DUPLICATE);
        assertThat(engine.breaks()).hasSize(1);
    }

    @Test
    void outOfOrderArrivalAcrossManyTradeRefsStillMatchesCorrectly() {
        // Interleave two trade references' legs out of order.
        engine.ingest(instruction("a1", "TRD-1", Side.PARTY_A, "100.00"));
        engine.ingest(instruction("b1", "TRD-2", Side.PARTY_B, "50.00"));
        engine.ingest(instruction("b2", "TRD-1", Side.PARTY_B, "100.00"));
        engine.ingest(instruction("a2", "TRD-2", Side.PARTY_A, "50.00"));

        assertThat(engine.matchedPositions()).hasSize(2);
        assertThat(engine.breaks()).isEmpty();
        assertThat(engine.stillAwaitingCounterpart()).isZero();
    }
}
