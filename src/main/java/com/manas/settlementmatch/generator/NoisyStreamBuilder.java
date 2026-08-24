package com.manas.settlementmatch.generator;

import com.manas.settlementmatch.model.SettlementInstruction;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Turns a canonical, in-order instruction stream into a noisy one: a fixed
 * fraction of messages are redelivered as exact duplicates (same messageId,
 * simulating broker/producer at-least-once redelivery, not a distinct new
 * message), and the whole stream is shuffled out of trade-reference order.
 * Used for the idempotent-replay measurement (claim 6): the matcher's final
 * state after consuming this stream must equal its final state after
 * consuming the canonical stream.
 */
public final class NoisyStreamBuilder {

    private NoisyStreamBuilder() {
    }

    public static List<SettlementInstruction> buildNoisyStream(List<SettlementInstruction> canonical, double duplicateFraction, long seed) {
        Random random = new Random(seed);
        List<SettlementInstruction> withDuplicates = new ArrayList<>(canonical);

        int duplicateCount = (int) Math.round(canonical.size() * duplicateFraction);
        for (int i = 0; i < duplicateCount; i++) {
            SettlementInstruction original = canonical.get(random.nextInt(canonical.size()));
            withDuplicates.add(original); // same messageId: a genuine redelivery, not a new message
        }

        java.util.Collections.shuffle(withDuplicates, random);
        return withDuplicates;
    }
}
