package com.manas.settlementmatch.goldencopy;

import java.util.Map;

/**
 * The full declared per-field survivorship precedence table: every
 * {@link GoldenField} must have a rule, so a field can never fall through
 * to undefined behavior for lack of configuration.
 */
public final class SurvivorshipRuleSet {

    private final Map<GoldenField, SurvivorshipRule> rules;

    public SurvivorshipRuleSet(Map<GoldenField, SurvivorshipRule> rules) {
        for (GoldenField field : GoldenField.values()) {
            if (!rules.containsKey(field)) {
                throw new IllegalArgumentException("no survivorship rule declared for golden field: " + field.fieldName());
            }
        }
        this.rules = Map.copyOf(rules);
    }

    public SurvivorshipRule ruleFor(GoldenField field) {
        return rules.get(field);
    }
}
