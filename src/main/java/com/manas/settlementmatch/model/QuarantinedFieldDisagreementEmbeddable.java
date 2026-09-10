package com.manas.settlementmatch.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * One canonical field on which the FIX, FpML-style, and delimited
 * descriptions of a quarantined trade disagreed, with all three conflicting
 * values, each independently queryable rather than packed into a
 * delimited string.
 */
@Embeddable
public class QuarantinedFieldDisagreementEmbeddable {

    @Column(name = "field_name", nullable = false)
    private String fieldName;

    @Column(name = "fix_value", nullable = false)
    private String fixValue;

    @Column(name = "fpml_value", nullable = false)
    private String fpmlValue;

    @Column(name = "delimited_value", nullable = false)
    private String delimitedValue;

    protected QuarantinedFieldDisagreementEmbeddable() {
        // JPA
    }

    public QuarantinedFieldDisagreementEmbeddable(String fieldName, String fixValue, String fpmlValue, String delimitedValue) {
        this.fieldName = fieldName;
        this.fixValue = fixValue;
        this.fpmlValue = fpmlValue;
        this.delimitedValue = delimitedValue;
    }

    public String getFieldName() {
        return fieldName;
    }

    public String getFixValue() {
        return fixValue;
    }

    public String getFpmlValue() {
        return fpmlValue;
    }

    public String getDelimitedValue() {
        return delimitedValue;
    }
}
