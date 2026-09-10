package com.manas.settlementmatch.gateway;

/**
 * The outcome when a trade reference's FIX, FpML-style, and delimited
 * descriptions all agree on every canonical field: {@code canonical} is any
 * one of the three (they are identical by definition of reaching this
 * state), kept so downstream code has a single normalized record without
 * caring which format happened to trigger completion.
 */
public record ReconciledTrade(String tradeRef, NormalizedTradeRecord canonical) {
}
