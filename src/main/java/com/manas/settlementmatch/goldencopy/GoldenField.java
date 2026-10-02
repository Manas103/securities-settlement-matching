package com.manas.settlementmatch.goldencopy;

/**
 * The seven fields the golden security master consolidates. This is the
 * one place that knows how to read a given field off a {@link VendorFeedRecord};
 * {@link FieldSurvivorshipResolver} never contains a field-specific branch.
 */
public enum GoldenField {
    NAME, COUNTRY, CURRENCY, SECTOR, MATURITY, COUPON, EXCHANGE;

    /** The key this field is addressed by in {@code goldencopy-survivorship-rules.yml} and the REST API. */
    public String fieldName() {
        return switch (this) {
            case NAME -> "name";
            case COUNTRY -> "country";
            case CURRENCY -> "currency";
            case SECTOR -> "sector";
            case MATURITY -> "maturity";
            case COUPON -> "coupon";
            case EXCHANGE -> "exchange";
        };
    }

    public Object valueFrom(VendorFeedRecord record) {
        return switch (this) {
            case NAME -> record.name();
            case COUNTRY -> record.country();
            case CURRENCY -> record.currency();
            case SECTOR -> record.sector();
            case MATURITY -> record.maturity();
            case COUPON -> record.coupon();
            case EXCHANGE -> record.exchange();
        };
    }

    public static GoldenField fromFieldName(String name) {
        for (GoldenField field : values()) {
            if (field.fieldName().equals(name)) {
                return field;
            }
        }
        throw new IllegalArgumentException("unknown golden field: " + name);
    }
}
