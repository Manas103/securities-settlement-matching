package com.manas.settlementmatch.gateway;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * The canonical trade description every wire format is normalized into
 * before the cross-format reconciliation gate compares them. This is a
 * superset of {@link com.manas.settlementmatch.model.SettlementInstruction}:
 * it adds {@code sourceFormat} and {@code sourceMessageId} (which wire
 * message this came from, for quarantine reporting) and {@code account},
 * which the delimited and FpML feeds carry explicitly and which
 * {@link NormalizedTradeRecordMapper} folds into
 * {@code SettlementInstruction.counterparty()} when a reconciled record is
 * handed to the existing two-leg matching engine.
 *
 * <p>{@code tradeRef} is the join key across all three formats: the same
 * trade, described three times, must carry the same trade reference in
 * every format for the gate to ever group them together. A trade reference
 * that shows up in only one or two formats is not a bug, it is simply
 * incomplete and stays buffered (see {@link CrossFormatReconciliationGate}).
 */
public record NormalizedTradeRecord(
        String tradeRef,
        SourceFormat sourceFormat,
        String sourceMessageId,
        String isin,
        long quantity,
        BigDecimal price,
        LocalDate settlementDate,
        String currency,
        String account
) {
}
