package com.manas.settlementmatch.gateway;

import com.manas.settlementmatch.generator.CrossFormatTradeGenerator;

import java.util.ArrayList;
import java.util.List;

/**
 * Test-only glue between {@link CrossFormatTradeGenerator}'s raw wire
 * strings and the three real parsers. Kept out of {@code src/main}
 * deliberately: the generator produces raw messages the way an upstream
 * system would, and it is the parsers' job, not the generator's, to know
 * how to read them.
 */
final class CrossFormatTestSupport {

    private static final FixAllocationMessageParser FIX_PARSER = new FixAllocationMessageParser();
    private static final FpmlConfirmationParser FPML_PARSER = new FpmlConfirmationParser();
    private static final DelimitedPostTradeFileParser DELIMITED_PARSER = new DelimitedPostTradeFileParser();

    private CrossFormatTestSupport() {
    }

    static List<NormalizedTradeRecord> parseOneTrade(CrossFormatTradeGenerator.RawTradeMessages raw) {
        return List.of(
                FIX_PARSER.parse(raw.fixMessage()),
                FPML_PARSER.parse(raw.fpmlXml()),
                DELIMITED_PARSER.parse(raw.delimitedLine()));
    }

    static List<NormalizedTradeRecord> parseAll(List<CrossFormatTradeGenerator.RawTradeMessages> trades) {
        List<NormalizedTradeRecord> out = new ArrayList<>(trades.size() * 3);
        for (CrossFormatTradeGenerator.RawTradeMessages raw : trades) {
            out.addAll(parseOneTrade(raw));
        }
        return out;
    }
}
