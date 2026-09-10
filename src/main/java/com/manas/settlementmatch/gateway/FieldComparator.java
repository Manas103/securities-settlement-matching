package com.manas.settlementmatch.gateway;

import java.util.ArrayList;
import java.util.List;

/**
 * The one place that knows which canonical fields the cross-format gate
 * compares and how each is rendered into a human-readable value for a
 * {@link FieldDisagreement}. Shared, byte-for-byte, between
 * {@link CrossFormatReconciliationGate} (the fast, hash-indexed path) and
 * {@link ReferenceOracleReconciliationGate} (the deliberately slow oracle)
 * so a divergence between the two can only come from how they group and
 * buffer records, never from the two paths disagreeing about what "the
 * same" means for a given field.
 */
final class FieldComparator {

    private FieldComparator() {
    }

    static List<FieldDisagreement> compare(NormalizedTradeRecord fix, NormalizedTradeRecord fpml, NormalizedTradeRecord delimited) {
        List<FieldDisagreement> disagreements = new ArrayList<>();
        checkField(disagreements, "isin", fix.isin(), fpml.isin(), delimited.isin());
        checkField(disagreements, "quantity", String.valueOf(fix.quantity()), String.valueOf(fpml.quantity()), String.valueOf(delimited.quantity()));
        checkField(disagreements, "price", fix.price().stripTrailingZeros().toPlainString(),
                fpml.price().stripTrailingZeros().toPlainString(), delimited.price().stripTrailingZeros().toPlainString());
        checkField(disagreements, "settlementDate", fix.settlementDate().toString(), fpml.settlementDate().toString(), delimited.settlementDate().toString());
        checkField(disagreements, "currency", fix.currency(), fpml.currency(), delimited.currency());
        checkField(disagreements, "account", fix.account(), fpml.account(), delimited.account());
        return disagreements;
    }

    private static void checkField(List<FieldDisagreement> out, String fieldName, String fixValue, String fpmlValue, String delimitedValue) {
        if (!fixValue.equals(fpmlValue) || !fpmlValue.equals(delimitedValue)) {
            out.add(new FieldDisagreement(fieldName, fixValue, fpmlValue, delimitedValue));
        }
    }
}
