package com.manas.settlementmatch.goldencopy;

import java.util.List;

/** The consolidated golden record for one security: its identifiers, and one {@link FieldResolution} per golden field. */
public record GoldenSecurityRecord(String internalKey, SecurityIdentifiers identifiers, List<FieldResolution> fieldResolutions) {

    public FieldResolution resolutionFor(GoldenField field) {
        for (FieldResolution resolution : fieldResolutions) {
            if (resolution.field() == field) {
                return resolution;
            }
        }
        throw new IllegalStateException("no resolution computed for field " + field.fieldName());
    }

    public long heldCount() {
        return fieldResolutions.stream().filter(r -> r.outcome() == FieldOutcome.HELD).count();
    }

    public long resolvedCount() {
        return fieldResolutions.stream().filter(r -> r.outcome() == FieldOutcome.RESOLVED).count();
    }
}
