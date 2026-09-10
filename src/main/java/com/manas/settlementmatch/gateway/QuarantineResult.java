package com.manas.settlementmatch.gateway;

import java.util.List;

/**
 * The outcome when a trade reference's three format descriptions have all
 * arrived but disagree on at least one canonical field. The trade's
 * ingestion is quarantined rather than guessed at: no format is treated as
 * "more trustworthy", and nothing is passed on to the existing break-match
 * pipeline until an operator resolves which value is correct.
 */
public record QuarantineResult(
        String tradeRef,
        List<FieldDisagreement> disagreements,
        NormalizedTradeRecord fixRecord,
        NormalizedTradeRecord fpmlRecord,
        NormalizedTradeRecord delimitedRecord
) {
    public String fieldNames() {
        return disagreements.stream()
                .map(FieldDisagreement::fieldName)
                .sorted()
                .reduce((a, b) -> a + "," + b)
                .orElse("");
    }
}
