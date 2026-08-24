package com.manas.settlementmatch.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * One disagreeing field on a filed {@link BreakEntity}, stored as an
 * element-collection row rather than a delimited string, so the exact
 * field, both values, the delta, and the tolerance that was exceeded are
 * each independently queryable.
 */
@Embeddable
public class BreakFieldDiscrepancyEmbeddable {

    @Column(name = "field_name", nullable = false)
    private String fieldName;

    @Column(name = "party_a_value", nullable = false)
    private String partyAValue;

    @Column(name = "party_b_value", nullable = false)
    private String partyBValue;

    @Column(name = "delta", nullable = false)
    private String delta;

    @Column(name = "tolerance_description", nullable = false)
    private String toleranceDescription;

    protected BreakFieldDiscrepancyEmbeddable() {
        // JPA
    }

    public BreakFieldDiscrepancyEmbeddable(String fieldName, String partyAValue, String partyBValue, String delta, String toleranceDescription) {
        this.fieldName = fieldName;
        this.partyAValue = partyAValue;
        this.partyBValue = partyBValue;
        this.delta = delta;
        this.toleranceDescription = toleranceDescription;
    }

    public String getFieldName() {
        return fieldName;
    }

    public String getPartyAValue() {
        return partyAValue;
    }

    public String getPartyBValue() {
        return partyBValue;
    }

    public String getDelta() {
        return delta;
    }

    public String getToleranceDescription() {
        return toleranceDescription;
    }
}
