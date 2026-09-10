package com.manas.settlementmatch.generator;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Turns a canonical, in-order stream of messages into a noisy one: a fixed
 * fraction of messages are redelivered as exact duplicates (same identity
 * key, simulating broker/producer at-least-once redelivery, not a distinct
 * new message), and the whole stream is shuffled out of order. Used for
 * every idempotent-replay measurement in this repository: both the
 * two-legs-by-reference matching engine (claim 6, {@code SettlementInstruction})
 * and the cross-format reconciliation gate (the false-hold-rate and
 * quarantine benchmarks, {@code NormalizedTradeRecord}) need exactly this
 * shape of noise, so the type is generic rather than duplicated per record
 * type.
 */
public final class NoisyStreamBuilder {

    private NoisyStreamBuilder() {
    }

    public static <T> List<T> buildNoisyStream(List<T> canonical, double duplicateFraction, long seed) {
        Random random = new Random(seed);
        List<T> withDuplicates = new ArrayList<>(canonical);

        int duplicateCount = (int) Math.round(canonical.size() * duplicateFraction);
        for (int i = 0; i < duplicateCount; i++) {
            T original = canonical.get(random.nextInt(canonical.size()));
            withDuplicates.add(original); // same identity key: a genuine redelivery, not a new message
        }

        java.util.Collections.shuffle(withDuplicates, random);
        return withDuplicates;
    }
}
