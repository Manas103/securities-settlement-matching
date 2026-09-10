package com.manas.settlementmatch.gateway;

/**
 * Thrown by a {@link TradeMessageParser} when a raw message is missing a
 * required field or the field cannot be parsed into its canonical type.
 * Deliberately not a checked exception: a malformed message from an
 * upstream feed is an operational event to alert on, not a recoverable
 * condition a caller is expected to branch on inline.
 */
public class MalformedTradeMessageException extends RuntimeException {
    public MalformedTradeMessageException(String message) {
        super(message);
    }

    public MalformedTradeMessageException(String message, Throwable cause) {
        super(message, cause);
    }
}
