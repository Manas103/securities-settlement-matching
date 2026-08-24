package com.manas.settlementmatch.tolerance;

import com.manas.settlementmatch.model.SettlementInstruction;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The configurable tolerance ruleset. One rule per compared field, keyed by
 * field name, loaded from {@code tolerance-rules.yml} at startup by
 * {@link ToleranceRuleSetLoader}. Comparing two instructions never branches
 * on which field it is beyond dispatching to the field's configured
 * {@link ComparisonType}: adding a new field or loosening a tolerance is a
 * one-line YAML edit, not a code change.
 */
public final class ToleranceRuleSet {

    private final Map<String, ToleranceRule> rulesByField;

    public ToleranceRuleSet(Map<String, ToleranceRule> rulesByField) {
        this.rulesByField = Map.copyOf(rulesByField);
    }

    public ToleranceRule ruleFor(String fieldName) {
        ToleranceRule rule = rulesByField.get(fieldName);
        if (rule == null) {
            throw new IllegalStateException("no tolerance rule configured for field '" + fieldName + "'");
        }
        return rule;
    }

    /**
     * Compares every configured field of the two sides of one settlement
     * instruction and returns one {@link FieldDiscrepancy} per field that
     * disagreed beyond its configured tolerance. An empty list means the
     * instruction matches.
     */
    public List<FieldDiscrepancy> compare(SettlementInstruction a, SettlementInstruction b) {
        List<FieldDiscrepancy> discrepancies = new ArrayList<>();
        compareExactString(discrepancies, "isin", a.isin(), b.isin());
        compareExactString(discrepancies, "currency", a.currency(), b.currency());
        compareDate(discrepancies, "settlementDate", a.settlementDate(), b.settlementDate());
        compareQuantity(discrepancies, a.quantity(), b.quantity());
        comparePrice(discrepancies, a.price(), b.price());
        return discrepancies;
    }

    private void compareExactString(List<FieldDiscrepancy> out, String field, String aValue, String bValue) {
        ToleranceRule rule = ruleFor(field);
        if (rule.comparison() != ComparisonType.EXACT) {
            throw new IllegalStateException("field '" + field + "' must be configured EXACT, got " + rule.comparison());
        }
        if (!aValue.equals(bValue)) {
            out.add(new FieldDiscrepancy(field, aValue, bValue, "n/a", "exact match required"));
        }
    }

    private void compareDate(List<FieldDiscrepancy> out, String field, LocalDate aValue, LocalDate bValue) {
        ToleranceRule rule = ruleFor(field);
        if (rule.comparison() != ComparisonType.EXACT) {
            throw new IllegalStateException("field '" + field + "' must be configured EXACT, got " + rule.comparison());
        }
        if (!aValue.equals(bValue)) {
            out.add(new FieldDiscrepancy(field, aValue.toString(), bValue.toString(), "n/a", "exact match required"));
        }
    }

    private void compareQuantity(List<FieldDiscrepancy> out, long aValue, long bValue) {
        ToleranceRule rule = ruleFor("quantity");
        switch (rule.comparison()) {
            case EXACT -> {
                if (aValue != bValue) {
                    out.add(new FieldDiscrepancy("quantity", String.valueOf(aValue), String.valueOf(bValue),
                            String.valueOf(bValue - aValue), "exact match required"));
                }
            }
            case ABSOLUTE -> {
                long delta = Math.abs(aValue - bValue);
                if (BigDecimal.valueOf(delta).compareTo(rule.toleranceValue()) > 0) {
                    out.add(new FieldDiscrepancy("quantity", String.valueOf(aValue), String.valueOf(bValue),
                            String.valueOf(bValue - aValue), "±" + rule.toleranceValue() + " units"));
                }
            }
            default -> throw new IllegalStateException("quantity does not support " + rule.comparison());
        }
    }

    private void comparePrice(List<FieldDiscrepancy> out, BigDecimal aValue, BigDecimal bValue) {
        ToleranceRule rule = ruleFor("price");
        BigDecimal delta = bValue.subtract(aValue).abs();
        switch (rule.comparison()) {
            case EXACT -> {
                if (aValue.compareTo(bValue) != 0) {
                    out.add(new FieldDiscrepancy("price", aValue.toPlainString(), bValue.toPlainString(),
                            delta.toPlainString(), "exact match required"));
                }
            }
            case ABSOLUTE -> {
                if (delta.compareTo(rule.toleranceValue()) > 0) {
                    out.add(new FieldDiscrepancy("price", aValue.toPlainString(), bValue.toPlainString(),
                            delta.toPlainString(), "±" + rule.toleranceValue().toPlainString()));
                }
            }
            case RELATIVE_BPS -> {
                BigDecimal average = aValue.add(bValue).divide(BigDecimal.valueOf(2), MathContext.DECIMAL64);
                BigDecimal observedBps;
                if (average.compareTo(BigDecimal.ZERO) == 0) {
                    // Both sides quoted zero: only exact equality (already true, since delta would be zero
                    // too) can be within tolerance; guard the division instead of throwing on a real feed value.
                    observedBps = delta.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(Long.MAX_VALUE);
                } else {
                    observedBps = delta.divide(average.abs(), MathContext.DECIMAL64)
                            .multiply(BigDecimal.valueOf(10_000))
                            .setScale(4, RoundingMode.HALF_UP);
                }
                if (observedBps.compareTo(rule.toleranceValue()) > 0) {
                    out.add(new FieldDiscrepancy("price", aValue.toPlainString(), bValue.toPlainString(),
                            delta.toPlainString() + " (" + observedBps.toPlainString() + " bps)",
                            "±" + rule.toleranceValue().toPlainString() + " bps"));
                }
            }
        }
    }
}
