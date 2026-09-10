package com.manas.settlementmatch.gateway;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FpmlConfirmationParserTest {

    private final FpmlConfirmationParser parser = new FpmlConfirmationParser();

    private String confirmation(String tradeRef, String isin, String qty, String price, String date, String ccy, String account, String msgId) {
        return """
                <tradeConfirmation>
                  <tradeReference>%s</tradeReference>
                  <instrument><isin>%s</isin></instrument>
                  <quantity>%s</quantity>
                  <price>%s</price>
                  <settlementDate>%s</settlementDate>
                  <currency>%s</currency>
                  <account>%s</account>
                  <messageId>%s</messageId>
                </tradeConfirmation>
                """.formatted(tradeRef, isin, qty, price, date, ccy, account, msgId);
    }

    @Test
    void parsesAWellFormedConfirmation() {
        String xml = confirmation("TRD-00000001", "US0378331005", "1000000", "99.750000", "2026-07-15", "USD", "BNY-CUST-01", "FPML00000001");

        NormalizedTradeRecord record = parser.parse(xml);

        assertThat(record.tradeRef()).isEqualTo("TRD-00000001");
        assertThat(record.sourceFormat()).isEqualTo(SourceFormat.FPML);
        assertThat(record.sourceMessageId()).isEqualTo("FPML00000001");
        assertThat(record.isin()).isEqualTo("US0378331005");
        assertThat(record.quantity()).isEqualTo(1_000_000L);
        assertThat(record.price()).isEqualByComparingTo(new BigDecimal("99.750000"));
        assertThat(record.settlementDate()).isEqualTo(LocalDate.of(2026, 7, 15));
        assertThat(record.currency()).isEqualTo("USD");
        assertThat(record.account()).isEqualTo("BNY-CUST-01");
    }

    @Test
    void missingElementIsRejected() {
        String xml = """
                <tradeConfirmation>
                  <tradeReference>TRD-1</tradeReference>
                  <instrument><isin>US0378331005</isin></instrument>
                  <quantity>100</quantity>
                  <settlementDate>2026-07-15</settlementDate>
                  <currency>USD</currency>
                  <account>BNY-CUST-01</account>
                  <messageId>FPML1</messageId>
                </tradeConfirmation>
                """;
        assertThatThrownBy(() -> parser.parse(xml))
                .isInstanceOf(MalformedTradeMessageException.class)
                .hasMessageContaining("price");
    }

    @Test
    void malformedXmlIsRejected() {
        assertThatThrownBy(() -> parser.parse("<tradeConfirmation><tradeReference>TRD-1</tradeReference>"))
                .isInstanceOf(MalformedTradeMessageException.class);
    }

    @Test
    void aDoctypeDeclarationIsRejectedRatherThanExpanded() {
        String xxePayload = """
                <?xml version="1.0"?>
                <!DOCTYPE tradeConfirmation [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
                <tradeConfirmation>
                  <tradeReference>&xxe;</tradeReference>
                </tradeConfirmation>
                """;
        assertThatThrownBy(() -> parser.parse(xxePayload)).isInstanceOf(MalformedTradeMessageException.class);
    }

    @Test
    void blankMessageIsRejected() {
        assertThatThrownBy(() -> parser.parse("")).isInstanceOf(MalformedTradeMessageException.class);
    }
}
