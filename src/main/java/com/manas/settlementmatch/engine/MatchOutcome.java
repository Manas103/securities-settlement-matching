package com.manas.settlementmatch.engine;

import com.manas.settlementmatch.model.SettlementInstruction;

/**
 * What happened to one ingested {@link SettlementInstruction} message.
 * DUPLICATE and BUFFERED never touch the matched/break stores; only
 * MATCHED and BREAK represent a completed instruction.
 */
public final class MatchOutcome {

    public enum Type { MATCHED, BREAK, BUFFERED, DUPLICATE }

    private final Type type;
    private final MatchedPositionResult matchedPosition;
    private final BreakResult breakResult;
    private final SettlementInstruction instruction;

    private MatchOutcome(Type type, MatchedPositionResult matchedPosition, BreakResult breakResult, SettlementInstruction instruction) {
        this.type = type;
        this.matchedPosition = matchedPosition;
        this.breakResult = breakResult;
        this.instruction = instruction;
    }

    public static MatchOutcome matched(MatchedPositionResult position) {
        return new MatchOutcome(Type.MATCHED, position, null, null);
    }

    public static MatchOutcome breakFiled(BreakResult breakResult) {
        return new MatchOutcome(Type.BREAK, null, breakResult, null);
    }

    public static MatchOutcome buffered(SettlementInstruction instruction) {
        return new MatchOutcome(Type.BUFFERED, null, null, instruction);
    }

    public static MatchOutcome duplicate(SettlementInstruction instruction) {
        return new MatchOutcome(Type.DUPLICATE, null, null, instruction);
    }

    public Type type() {
        return type;
    }

    public MatchedPositionResult matchedPosition() {
        return matchedPosition;
    }

    public BreakResult breakResult() {
        return breakResult;
    }

    public SettlementInstruction instruction() {
        return instruction;
    }
}
