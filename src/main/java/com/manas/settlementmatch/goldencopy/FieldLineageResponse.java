package com.manas.settlementmatch.goldencopy;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "One golden field's resolved value and lineage, or, if held, the named data owner it is held for.")
public record FieldLineageResponse(
        String field,
        String outcome,
        String value,
        String sourceVendor,
        String sourceVendorRecordId,
        String heldForOwner
) {
    public static FieldLineageResponse from(FieldResolution resolution) {
        return new FieldLineageResponse(
                resolution.field().fieldName(),
                resolution.outcome().name(),
                resolution.renderedValue(),
                resolution.sourceVendor() == null ? null : resolution.sourceVendor().name(),
                resolution.sourceVendorRecordId(),
                resolution.heldForOwner());
    }
}
