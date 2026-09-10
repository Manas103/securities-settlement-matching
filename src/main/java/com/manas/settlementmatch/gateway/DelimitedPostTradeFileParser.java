package com.manas.settlementmatch.gateway;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * Parses one line of a custodian post-trade flat file: eight
 * comma-separated fields in a fixed order, no header row, no quoting or
 * escaping (a real custodian file of this vintage typically has neither).
 * There is no existing flat-file ingestion in this repository to match, so
 * this is a new, deliberately simple fixed-format:
 *
 * <pre>tradeRef,isin,quantity,price,settlementDate,currency,account,messageId</pre>
 *
 * <p>Example: {@code TRD-00000001,US0378331005,1000000,99.750000,2026-07-15,USD,BNY-CUST-01,POST-00000001}
 */
public final class DelimitedPostTradeFileParser implements TradeMessageParser {

    private static final int FIELD_COUNT = 8;

    @Override
    public SourceFormat format() {
        return SourceFormat.DELIMITED;
    }

    @Override
    public NormalizedTradeRecord parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new MalformedTradeMessageException("delimited post-trade line is blank");
        }
        String[] fields = raw.split(",", -1);
        if (fields.length != FIELD_COUNT) {
            throw new MalformedTradeMessageException(
                    "delimited post-trade line has " + fields.length + " fields, expected " + FIELD_COUNT + ": " + raw);
        }
        for (String field : fields) {
            if (field.isBlank()) {
                throw new MalformedTradeMessageException("delimited post-trade line has a blank field: " + raw);
            }
        }

        try {
            String tradeRef = fields[0].trim();
            String isin = fields[1].trim();
            long quantity = Long.parseLong(fields[2].trim());
            BigDecimal price = new BigDecimal(fields[3].trim());
            LocalDate settlementDate = LocalDate.parse(fields[4].trim());
            String currency = fields[5].trim();
            String account = fields[6].trim();
            String messageId = fields[7].trim();

            return new NormalizedTradeRecord(tradeRef, SourceFormat.DELIMITED, messageId, isin, quantity, price,
                    settlementDate, currency, account);
        } catch (NumberFormatException | DateTimeParseException e) {
            throw new MalformedTradeMessageException("delimited post-trade line has an unparseable field: " + raw, e);
        }
    }
}
