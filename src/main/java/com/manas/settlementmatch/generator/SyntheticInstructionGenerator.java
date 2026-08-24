package com.manas.settlementmatch.generator;

import com.manas.settlementmatch.model.Side;
import com.manas.settlementmatch.model.SettlementInstruction;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Deterministic, seeded synthetic settlement instruction traffic. Every
 * number in this project's README traces back to a run of this generator;
 * given the same seed and pair count it always produces the same stream,
 * which is what makes the measurements reproducible rather than anecdotal.
 */
public final class SyntheticInstructionGenerator {

    public enum SeededMismatchField { PRICE, QUANTITY, SETTLEMENT_DATE, CURRENCY, ISIN }

    private static final String[] ISINS = {
            "US0378331005", "US5949181045", "US88160R1014", "US02079K3059", "US0231351067",
            "US64110L1061", "US30303M1027", "GB0002875804", "DE0007164600", "FR0000131104"
    };
    private static final String[] CURRENCIES = {"USD", "EUR", "GBP", "JPY", "CHF"};
    private static final String[] COUNTERPARTIES_A = {"BNY-CUST-01", "BNY-CUST-02", "BNY-CUST-03", "BNY-CUST-04"};
    private static final String[] COUNTERPARTIES_B = {"STREET-BRK-11", "STREET-BRK-12", "STREET-BRK-13", "STREET-BRK-14"};

    public record GeneratedStream(List<SettlementInstruction> instructions, List<String> seededBreakTradeRefs) {
    }

    /**
     * Generates {@code pairCount} trade-reference pairs (2 * pairCount
     * messages total). Exactly {@code seededBreakCount} of those pairs are
     * seeded with a field disagreement that exceeds the configured
     * tolerance, round-robining across all five mismatch field types so the
     * naming logic is exercised for every kind of break, not just one.
     */
    public GeneratedStream generate(int pairCount, int seededBreakCount, long seed) {
        if (seededBreakCount > pairCount) {
            throw new IllegalArgumentException("cannot seed more breaks than pairs");
        }
        Random random = new Random(seed);
        List<SettlementInstruction> out = new ArrayList<>(pairCount * 2);
        List<String> seededTradeRefs = new ArrayList<>(seededBreakCount);

        // Choose which pair indices are seeded breaks by evenly spacing them across the run,
        // rather than clustering them at the start, so the stream looks like real traffic.
        int stride = pairCount / seededBreakCount;
        boolean[] isSeededBreak = new boolean[pairCount];
        for (int i = 0; i < seededBreakCount; i++) {
            isSeededBreak[Math.min(i * stride, pairCount - 1)] = true;
        }

        long messageCounter = 0;
        int seededSoFar = 0;
        for (int i = 0; i < pairCount; i++) {
            String tradeRef = "TRD-%08d".formatted(i);

            String isin = ISINS[random.nextInt(ISINS.length)];
            long quantity = 100L + random.nextInt(9900);
            BigDecimal price = BigDecimal.valueOf(10 + random.nextDouble() * 990).setScale(4, RoundingMode.HALF_UP);
            LocalDate settlementDate = LocalDate.of(2026, 7, 1).plusDays(random.nextInt(20));
            String currency = CURRENCIES[random.nextInt(CURRENCIES.length)];

            SettlementInstruction partyA = new SettlementInstruction(
                    "MSG-A-%08d".formatted(i), tradeRef, Side.PARTY_A, isin, quantity, price,
                    settlementDate, currency, COUNTERPARTIES_A[random.nextInt(COUNTERPARTIES_A.length)], messageCounter++);

            SettlementInstruction partyB;
            if (isSeededBreak[i]) {
                SeededMismatchField field = SeededMismatchField.values()[seededSoFar % SeededMismatchField.values().length];
                seededSoFar++;
                // messageId uses the pair index i (like every other messageId in this method), NOT
                // messageCounter, which is a global running count and would produce a numeric suffix
                // that collides with a later, unrelated pair's legitimate "MSG-B-%08d".formatted(i)
                // messageId. See the README "what broke" section: this exact collision was caught by
                // the reference-oracle diff test, not eyeballed.
                partyB = applyMismatch(field, tradeRef, i, messageCounter++, isin, quantity, price, settlementDate, currency, random);
                seededTradeRefs.add(tradeRef);
            } else {
                partyB = new SettlementInstruction(
                        "MSG-B-%08d".formatted(i), tradeRef, Side.PARTY_B, isin, quantity, price,
                        settlementDate, currency, COUNTERPARTIES_B[random.nextInt(COUNTERPARTIES_B.length)], messageCounter++);
            }

            out.add(partyA);
            out.add(partyB);
        }

        return new GeneratedStream(out, seededTradeRefs);
    }

    private SettlementInstruction applyMismatch(SeededMismatchField field, String tradeRef, int pairIndex, long msgSeq,
                                                 String isin, long quantity, BigDecimal price, LocalDate settlementDate,
                                                 String currency, Random random) {
        String messageId = "MSG-B-%08d".formatted(pairIndex);
        String counterparty = COUNTERPARTIES_B[random.nextInt(COUNTERPARTIES_B.length)];
        return switch (field) {
            case PRICE -> {
                // Move price by at least 25 bps, comfortably clearing the configured 5 bps tolerance.
                BigDecimal moved = price.multiply(BigDecimal.valueOf(1.01)).setScale(4, RoundingMode.HALF_UP);
                yield new SettlementInstruction(messageId, tradeRef, Side.PARTY_B, isin, quantity, moved,
                        settlementDate, currency, counterparty, msgSeq);
            }
            case QUANTITY -> new SettlementInstruction(messageId, tradeRef, Side.PARTY_B, isin, quantity + 1,
                    price, settlementDate, currency, counterparty, msgSeq);
            case SETTLEMENT_DATE -> new SettlementInstruction(messageId, tradeRef, Side.PARTY_B, isin, quantity,
                    price, settlementDate.plusDays(1), currency, counterparty, msgSeq);
            case CURRENCY -> {
                String otherCurrency = currency.equals("USD") ? "EUR" : "USD";
                yield new SettlementInstruction(messageId, tradeRef, Side.PARTY_B, isin, quantity, price,
                        settlementDate, otherCurrency, counterparty, msgSeq);
            }
            case ISIN -> {
                String otherIsin = ISINS[(java.util.Arrays.asList(ISINS).indexOf(isin) + 1) % ISINS.length];
                yield new SettlementInstruction(messageId, tradeRef, Side.PARTY_B, otherIsin, quantity, price,
                        settlementDate, currency, counterparty, msgSeq);
            }
        };
    }
}
