package com.manas.settlementmatch.gateway;

/**
 * What happened to one ingested {@link NormalizedTradeRecord}. Mirrors the
 * shape of {@link com.manas.settlementmatch.engine.MatchOutcome} on purpose:
 * BUFFERED and DUPLICATE never touch the reconciled/quarantined stores,
 * only RECONCILED and QUARANTINED represent a completed trade reference
 * (one with all three formats present).
 */
public final class GateOutcome {

    public enum Type { RECONCILED, QUARANTINED, BUFFERED, DUPLICATE }

    private final Type type;
    private final ReconciledTrade reconciledTrade;
    private final QuarantineResult quarantineResult;
    private final NormalizedTradeRecord record;

    private GateOutcome(Type type, ReconciledTrade reconciledTrade, QuarantineResult quarantineResult, NormalizedTradeRecord record) {
        this.type = type;
        this.reconciledTrade = reconciledTrade;
        this.quarantineResult = quarantineResult;
        this.record = record;
    }

    public static GateOutcome reconciled(ReconciledTrade reconciledTrade) {
        return new GateOutcome(Type.RECONCILED, reconciledTrade, null, null);
    }

    public static GateOutcome quarantined(QuarantineResult quarantineResult) {
        return new GateOutcome(Type.QUARANTINED, null, quarantineResult, null);
    }

    public static GateOutcome buffered(NormalizedTradeRecord record) {
        return new GateOutcome(Type.BUFFERED, null, null, record);
    }

    public static GateOutcome duplicate(NormalizedTradeRecord record) {
        return new GateOutcome(Type.DUPLICATE, null, null, record);
    }

    public Type type() {
        return type;
    }

    public ReconciledTrade reconciledTrade() {
        return reconciledTrade;
    }

    public QuarantineResult quarantineResult() {
        return quarantineResult;
    }

    public NormalizedTradeRecord record() {
        return record;
    }
}
