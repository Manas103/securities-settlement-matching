package com.manas.settlementmatch.gateway;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DelimitedPostTradeFileParserTest {

    private final DelimitedPostTradeFileParser parser = new DelimitedPostTradeFileParser();

    @Test
    void parsesAWellFormedLine() {
        NormalizedTradeRecord record = parser.parse("TRD-00000001,US0378331005,1000000,99.750000,2026-07-15,USD,BNY-CUST-01,POST00000001");

        assertThat(record.tradeRef()).isEqualTo("TRD-00000001");
        assertThat(record.sourceFormat()).isEqualTo(SourceFormat.DELIMITED);
        assertThat(record.sourceMessageId()).isEqualTo("POST00000001");
        assertThat(record.isin()).isEqualTo("US0378331005");
        assertThat(record.quantity()).isEqualTo(1_000_000L);
        assertThat(record.price()).isEqualByComparingTo(new BigDecimal("99.750000"));
        assertThat(record.settlementDate()).isEqualTo(LocalDate.of(2026, 7, 15));
        assertThat(record.currency()).isEqualTo("USD");
        assertThat(record.account()).isEqualTo("BNY-CUST-01");
    }

    @Test
    void wrongFieldCountIsRejected() {
        assertThatThrownBy(() -> parser.parse("TRD-1,US0378331005,1000000,99.75,2026-07-15,USD"))
                .isInstanceOf(MalformedTradeMessageException.class)
                .hasMessageContaining("8");
    }

    @Test
    void blankFieldIsRejected() {
        assertThatThrownBy(() -> parser.parse("TRD-1,,1000000,99.75,2026-07-15,USD,BNY-CUST-01,POST1"))
                .isInstanceOf(MalformedTradeMessageException.class);
    }

    @Test
    void unparseablePriceIsRejected() {
        assertThatThrownBy(() -> parser.parse("TRD-1,US0378331005,1000000,not-a-price,2026-07-15,USD,BNY-CUST-01,POST1"))
                .isInstanceOf(MalformedTradeMessageException.class);
    }

    @Test
    void blankLineIsRejected() {
        assertThatThrownBy(() -> parser.parse("")).isInstanceOf(MalformedTradeMessageException.class);
    }
}
