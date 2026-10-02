package com.manas.settlementmatch.goldencopy;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "The consolidated golden security record, with per-field lineage back to the vendor record that supplied each value, or the named owner it is held for.")
public record GoldenSecurityRecordResponse(
        String internalKey,
        SecurityIdentifiers identifiers,
        List<FieldLineageResponse> fields
) {
    public static GoldenSecurityRecordResponse from(GoldenSecurityRecord record) {
        List<FieldLineageResponse> fields = record.fieldResolutions().stream()
                .map(FieldLineageResponse::from)
                .toList();
        return new GoldenSecurityRecordResponse(record.internalKey(), record.identifiers(), fields);
    }
}
