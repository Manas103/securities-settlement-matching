package com.manas.settlementmatch.gateway;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;

/**
 * Parses a simplified FIX allocation instruction message: pipe-delimited
 * {@code tag=value} pairs, the same structural framing real FIX tag=value
 * encoding uses (SOH-delimited in a real session; pipe here so the message
 * is readable and testable as a plain string), using real FIX tag numbers
 * for the fields this project cares about. This is not a FIX engine: there
 * is no session layer, no checksum (tag 10), no BeginString/BodyLength
 * envelope, and only the tags below are recognized.
 *
 * <p>Tags used (standard FIX 4.4 numbering):
 * <ul>
 *   <li>35 MsgType (expected {@code AS}, AllocationInstruction)</li>
 *   <li>880 TrdMatchID, used as the cross-format trade reference join key</li>
 *   <li>70 AllocID, used as the source message id for quarantine reporting</li>
 *   <li>55 Symbol, used as the instrument identifier (a real FIX allocation
 *       would separate Symbol from SecurityID/SecurityIDSource; this project
 *       treats the one identifier tag as the canonical "isin" field)</li>
 *   <li>38 OrderQty</li>
 *   <li>44 Price</li>
 *   <li>64 SettlDate, {@code YYYYMMDD}</li>
 *   <li>15 Currency</li>
 *   <li>79 AllocAccount</li>
 * </ul>
 */
public final class FixAllocationMessageParser implements TradeMessageParser {

    private static final DateTimeFormatter FIX_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private static final Map<String, String> REQUIRED_TAGS = Map.of(
            "880", "TrdMatchID",
            "70", "AllocID",
            "55", "Symbol",
            "38", "OrderQty",
            "44", "Price",
            "64", "SettlDate",
            "15", "Currency",
            "79", "AllocAccount"
    );

    @Override
    public SourceFormat format() {
        return SourceFormat.FIX;
    }

    @Override
    public NormalizedTradeRecord parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new MalformedTradeMessageException("FIX message is blank");
        }
        Map<String, String> tags = new HashMap<>();
        for (String field : raw.split("\\|")) {
            if (field.isBlank()) {
                continue;
            }
            int eq = field.indexOf('=');
            if (eq <= 0) {
                throw new MalformedTradeMessageException("FIX field is not tag=value: '" + field + "'");
            }
            tags.put(field.substring(0, eq).trim(), field.substring(eq + 1).trim());
        }

        for (Map.Entry<String, String> required : REQUIRED_TAGS.entrySet()) {
            if (!tags.containsKey(required.getKey())) {
                throw new MalformedTradeMessageException(
                        "FIX message missing required tag " + required.getKey() + " (" + required.getValue() + ")");
            }
        }

        try {
            return new NormalizedTradeRecord(
                    tags.get("880"),
                    SourceFormat.FIX,
                    tags.get("70"),
                    tags.get("55"),
                    Long.parseLong(tags.get("38")),
                    new BigDecimal(tags.get("44")),
                    LocalDate.parse(tags.get("64"), FIX_DATE),
                    tags.get("15"),
                    tags.get("79"));
        } catch (NumberFormatException | DateTimeParseException e) {
            throw new MalformedTradeMessageException("FIX message has an unparseable field: " + raw, e);
        }
    }
}
