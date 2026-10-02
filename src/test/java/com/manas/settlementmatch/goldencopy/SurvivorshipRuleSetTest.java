package com.manas.settlementmatch.goldencopy;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SurvivorshipRuleSetTest {

    private final SurvivorshipRuleSet ruleSet = SurvivorshipRuleSetLoader.loadFromClasspath("goldencopy-survivorship-rules.yml");

    @Test
    void everyGoldenFieldHasADeclaredRuleAndNamedOwner() {
        for (GoldenField field : GoldenField.values()) {
            SurvivorshipRule rule = ruleSet.ruleFor(field);
            assertThat(rule).isNotNull();
            assertThat(rule.dataOwner()).isNotBlank();
        }
    }

    @Test
    void sectorHasNoDeclaredPrecedence() {
        assertThat(ruleSet.ruleFor(GoldenField.SECTOR).precedence()).isEmpty();
    }

    @Test
    void exchangeIsASingleNonFallbackVendor() {
        assertThat(ruleSet.ruleFor(GoldenField.EXCHANGE).precedence()).containsExactly(Vendor.TICKER_VENDOR);
    }

    @Test
    void nameFallsBackAcrossAllFourVendors() {
        assertThat(ruleSet.ruleFor(GoldenField.NAME).precedence())
                .containsExactly(Vendor.CUSIP_VENDOR, Vendor.ISIN_VENDOR, Vendor.SEDOL_VENDOR, Vendor.TICKER_VENDOR);
    }

    @Test
    void missingRequiredFieldThrows() {
        assertThatThrownByIncompleteRuleSet();
    }

    private void assertThatThrownByIncompleteRuleSet() {
        java.util.Map<GoldenField, SurvivorshipRule> incomplete = new java.util.EnumMap<>(GoldenField.class);
        incomplete.put(GoldenField.NAME, new SurvivorshipRule(GoldenField.NAME, java.util.List.of(Vendor.CUSIP_VENDOR), "owner"));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new SurvivorshipRuleSet(incomplete))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
