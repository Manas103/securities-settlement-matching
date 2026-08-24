package com.manas.settlementmatch.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One side (leg) of a settlement instruction as it arrives off the Kafka topic.
 * Two of these, one PARTY_A and one PARTY_B sharing the same {@code tradeRef},
 * make up a complete instruction the matching engine can compare.
 *
 * {@code messageId} is the idempotency key: a message redelivered by the
 * broker (or replayed by a noisy producer) carries the same messageId and is
 * deduplicated before it ever reaches the matching logic. {@code sequenceNumber}
 * is informational only, it is not relied on for correctness because the engine
 * is explicitly required to match correctly regardless of arrival order.
 */
public record SettlementInstruction(
        String messageId,
        String tradeRef,
        Side side,
        String isin,
        long quantity,
        BigDecimal price,
        LocalDate settlementDate,
        String currency,
        String counterparty,
        long sequenceNumber
) {
}
