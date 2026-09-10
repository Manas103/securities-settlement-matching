package com.manas.settlementmatch.gateway;

/**
 * One wire format in, one canonical {@link NormalizedTradeRecord} out.
 * Every implementation is stateless and pure: given the same raw message it
 * always produces the same record, which is what lets
 * {@link ReferenceOracleReconciliationGate} and
 * {@link CrossFormatReconciliationGate} be driven from the same fixtures in
 * tests without any parser-level mocking.
 */
public interface TradeMessageParser {
    NormalizedTradeRecord parse(String raw);

    SourceFormat format();
}
