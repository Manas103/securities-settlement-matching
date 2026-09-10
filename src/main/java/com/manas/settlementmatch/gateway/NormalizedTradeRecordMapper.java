package com.manas.settlementmatch.gateway;

import com.manas.settlementmatch.model.SettlementInstruction;
import com.manas.settlementmatch.model.Side;

/**
 * Bridges a reconciled {@link NormalizedTradeRecord} into the existing
 * {@link SettlementInstruction} model so a trade that has cleared the
 * cross-format gate can be handed to the existing, unmodified
 * {@link com.manas.settlementmatch.engine.MatchingEngine} as one leg. The
 * gate's {@code account} field maps to {@code SettlementInstruction}'s
 * {@code counterparty} slot: both mean "which book/party this leg
 * belongs to", and adding a parallel field to {@code SettlementInstruction}
 * for the same concept would be exactly the kind of duplication the
 * "superset" framing in {@link NormalizedTradeRecord} is meant to avoid.
 */
public final class NormalizedTradeRecordMapper {

    private NormalizedTradeRecordMapper() {
    }

    public static SettlementInstruction toSettlementInstruction(NormalizedTradeRecord record, Side side, long sequenceNumber) {
        return new SettlementInstruction(
                record.sourceMessageId(),
                record.tradeRef(),
                side,
                record.isin(),
                record.quantity(),
                record.price(),
                record.settlementDate(),
                record.currency(),
                record.account(),
                sequenceNumber);
    }
}
