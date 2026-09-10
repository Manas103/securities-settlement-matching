package com.manas.settlementmatch.gateway;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FixAllocationMessageParserTest {

    private final FixAllocationMessageParser parser = new FixAllocationMessageParser();

    @Test
    void parsesAWellFormedAllocationMessage() {
        NormalizedTradeRecord record = parser.parse(
                "35=AS|880=TRD-00000001|70=ALLOC00000001|55=UST 10Y|38=1000000|44=99.750000|64=20260715|15=USD|79=BNY-CUST-01");

        assertThat(record.tradeRef()).isEqualTo("TRD-00000001");
        assertThat(record.sourceFormat()).isEqualTo(SourceFormat.FIX);
        assertThat(record.sourceMessageId()).isEqualTo("ALLOC00000001");
        assertThat(record.isin()).isEqualTo("UST 10Y");
        assertThat(record.quantity()).isEqualTo(1_000_000L);
        assertThat(record.price()).isEqualByComparingTo(new BigDecimal("99.750000"));
        assertThat(record.settlementDate()).isEqualTo(LocalDate.of(2026, 7, 15));
        assertThat(record.currency()).isEqualTo("USD");
        assertThat(record.account()).isEqualTo("BNY-CUST-01");
    }

    @Test
    void missingRequiredTagIsRejected() {
        assertThatThrownBy(() -> parser.parse("35=AS|880=TRD-00000001|70=ALLOC00000001|55=UST 10Y|38=1000000|64=20260715|15=USD|79=BNY-CUST-01"))
                .isInstanceOf(MalformedTradeMessageException.class)
                .hasMessageContaining("44");
    }

    @Test
    void unparseableQuantityIsRejected() {
        assertThatThrownBy(() -> parser.parse(
                "35=AS|880=TRD-1|70=A1|55=UST 10Y|38=not-a-number|44=99.75|64=20260715|15=USD|79=BNY-CUST-01"))
                .isInstanceOf(MalformedTradeMessageException.class);
    }

    @Test
    void fieldWithoutAnEqualsSignIsRejected() {
        assertThatThrownBy(() -> parser.parse("35=AS|880TRD-1|70=A1"))
                .isInstanceOf(MalformedTradeMessageException.class)
                .hasMessageContaining("tag=value");
    }

    @Test
    void blankMessageIsRejected() {
        assertThatThrownBy(() -> parser.parse("  ")).isInstanceOf(MalformedTradeMessageException.class);
    }
}
