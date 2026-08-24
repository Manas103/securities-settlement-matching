package com.manas.settlementmatch.tolerance;

import com.manas.settlementmatch.model.Side;
import com.manas.settlementmatch.model.SettlementInstruction;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ToleranceRuleSetTest {

    private final ToleranceRuleSet ruleSet = ToleranceRuleSetLoader.loadFromClasspath("tolerance-rules.yml");

    private SettlementInstruction instruction(Side side, String isin, long qty, String price, LocalDate date, String ccy) {
        return new SettlementInstruction("msg-" + side, "TRD-1", side, isin, qty, new BigDecimal(price), date, ccy, "CPTY", 0L);
    }

    @Test
    void identicalSidesProduceNoDiscrepancies() {
        SettlementInstruction a = instruction(Side.PARTY_A, "US0378331005", 1000, "100.0000", LocalDate.of(2026, 7, 10), "USD");
        SettlementInstruction b = instruction(Side.PARTY_B, "US0378331005", 1000, "100.0000", LocalDate.of(2026, 7, 10), "USD");

        assertThat(ruleSet.compare(a, b)).isEmpty();
    }

    @Test
    void priceWithinFiveBpsIsNotADiscrepancy() {
        SettlementInstruction a = instruction(Side.PARTY_A, "US0378331005", 1000, "100.0000", LocalDate.of(2026, 7, 10), "USD");
        // 4 bps move: within the configured 5 bps tolerance
        SettlementInstruction b = instruction(Side.PARTY_B, "US0378331005", 1000, "100.0400", LocalDate.of(2026, 7, 10), "USD");

        assertThat(ruleSet.compare(a, b)).isEmpty();
    }

    @Test
    void priceBeyondFiveBpsIsNamedAsADiscrepancy() {
        SettlementInstruction a = instruction(Side.PARTY_A, "US0378331005", 1000, "100.0000", LocalDate.of(2026, 7, 10), "USD");
        // 100 bps move: well beyond the configured 5 bps tolerance
        SettlementInstruction b = instruction(Side.PARTY_B, "US0378331005", 1000, "101.0000", LocalDate.of(2026, 7, 10), "USD");

        List<FieldDiscrepancy> diffs = ruleSet.compare(a, b);

        assertThat(diffs).hasSize(1);
        FieldDiscrepancy d = diffs.get(0);
        assertThat(d.fieldName()).isEqualTo("price");
        assertThat(d.partyAValue()).isEqualTo("100.0000");
        assertThat(d.partyBValue()).isEqualTo("101.0000");
        assertThat(d.toleranceDescription()).contains("5");
    }

    @Test
    void quantityMustMatchExactly() {
        SettlementInstruction a = instruction(Side.PARTY_A, "US0378331005", 1000, "100.0000", LocalDate.of(2026, 7, 10), "USD");
        SettlementInstruction b = instruction(Side.PARTY_B, "US0378331005", 999, "100.0000", LocalDate.of(2026, 7, 10), "USD");

        List<FieldDiscrepancy> diffs = ruleSet.compare(a, b);

        assertThat(diffs).hasSize(1);
        assertThat(diffs.get(0).fieldName()).isEqualTo("quantity");
        assertThat(diffs.get(0).partyAValue()).isEqualTo("1000");
        assertThat(diffs.get(0).partyBValue()).isEqualTo("999");
    }

    @Test
    void isinMustMatchExactly() {
        SettlementInstruction a = instruction(Side.PARTY_A, "US0378331005", 1000, "100.0000", LocalDate.of(2026, 7, 10), "USD");
        SettlementInstruction b = instruction(Side.PARTY_B, "US5949181045", 1000, "100.0000", LocalDate.of(2026, 7, 10), "USD");

        List<FieldDiscrepancy> diffs = ruleSet.compare(a, b);

        assertThat(diffs).hasSize(1);
        assertThat(diffs.get(0).fieldName()).isEqualTo("isin");
    }

    @Test
    void settlementDateMustMatchExactly() {
        SettlementInstruction a = instruction(Side.PARTY_A, "US0378331005", 1000, "100.0000", LocalDate.of(2026, 7, 10), "USD");
        SettlementInstruction b = instruction(Side.PARTY_B, "US0378331005", 1000, "100.0000", LocalDate.of(2026, 7, 11), "USD");

        List<FieldDiscrepancy> diffs = ruleSet.compare(a, b);

        assertThat(diffs).hasSize(1);
        assertThat(diffs.get(0).fieldName()).isEqualTo("settlementDate");
    }

    @Test
    void currencyMustMatchExactly() {
        SettlementInstruction a = instruction(Side.PARTY_A, "US0378331005", 1000, "100.0000", LocalDate.of(2026, 7, 10), "USD");
        SettlementInstruction b = instruction(Side.PARTY_B, "US0378331005", 1000, "100.0000", LocalDate.of(2026, 7, 10), "EUR");

        List<FieldDiscrepancy> diffs = ruleSet.compare(a, b);

        assertThat(diffs).hasSize(1);
        assertThat(diffs.get(0).fieldName()).isEqualTo("currency");
    }

    @Test
    void multipleDisagreeingFieldsAreAllNamed() {
        SettlementInstruction a = instruction(Side.PARTY_A, "US0378331005", 1000, "100.0000", LocalDate.of(2026, 7, 10), "USD");
        SettlementInstruction b = instruction(Side.PARTY_B, "US5949181045", 999, "101.0000", LocalDate.of(2026, 7, 11), "EUR");

        List<FieldDiscrepancy> diffs = ruleSet.compare(a, b);

        assertThat(diffs).extracting(FieldDiscrepancy::fieldName)
                .containsExactlyInAnyOrder("isin", "currency", "settlementDate", "quantity", "price");
    }
}
