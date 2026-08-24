package com.manas.settlementmatch.engine;

import com.manas.settlementmatch.model.SettlementInstruction;
import com.manas.settlementmatch.tolerance.FieldDiscrepancy;

import java.util.List;

/**
 * The outcome of two sides disagreeing beyond their configured tolerance:
 * a filed break, naming every field that disagreed.
 */
public record BreakResult(
        String tradeRef,
        SettlementInstruction partyA,
        SettlementInstruction partyB,
        List<FieldDiscrepancy> discrepancies
) {
    public String fieldNames() {
        return discrepancies.stream().map(FieldDiscrepancy::fieldName).sorted().reduce((x, y) -> x + "," + y).orElse("");
    }
}
