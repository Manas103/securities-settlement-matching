package com.manas.settlementmatch.engine;

import com.manas.settlementmatch.model.SettlementInstruction;

/**
 * The outcome of two sides agreeing within tolerance: a settled position.
 * {@code partyA} and {@code partyB} are normalized so {@code partyA} is
 * always the {@code Side.PARTY_A} leg regardless of which one physically
 * arrived first.
 */
public record MatchedPositionResult(String tradeRef, SettlementInstruction partyA, SettlementInstruction partyB) {
}
