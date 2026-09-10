package com.manas.settlementmatch.generator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Deterministic, seeded synthetic traffic for the multi-protocol gateway:
 * for each trade reference, generates the actual raw FIX allocation
 * message, FpML-style XML confirmation, and delimited post-trade line that
 * {@link com.manas.settlementmatch.gateway.FixAllocationMessageParser},
 * {@link com.manas.settlementmatch.gateway.FpmlConfirmationParser}, and
 * {@link com.manas.settlementmatch.gateway.DelimitedPostTradeFileParser}
 * parse, not pre-built {@code NormalizedTradeRecord}s. This is deliberate:
 * the 30/30 quarantine claim and the false-hold-rate claim are only
 * meaningful if the disagreement was seeded into a raw wire message and
 * survived a genuine parse, not injected after normalization.
 */
public final class CrossFormatTradeGenerator {

    public enum SeededDisagreementField {
        ISIN, QUANTITY, PRICE, SETTLEMENT_DATE, CURRENCY, ACCOUNT;

        /** The canonical field name {@link com.manas.settlementmatch.gateway.FieldDisagreement#fieldName()} uses. */
        public String canonicalFieldName() {
            return switch (this) {
                case ISIN -> "isin";
                case QUANTITY -> "quantity";
                case PRICE -> "price";
                case SETTLEMENT_DATE -> "settlementDate";
                case CURRENCY -> "currency";
                case ACCOUNT -> "account";
            };
        }
    }

    public enum DisagreeingFormat { FIX, FPML, DELIMITED }

    private static final DateTimeFormatter FIX_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final String[] ISINS = {
            "US0378331005", "US5949181045", "US88160R1014", "US02079K3059", "US0231351067",
            "US64110L1061", "US30303M1027", "GB0002875804", "DE0007164600", "FR0000131104"
    };
    private static final String[] CURRENCIES = {"USD", "EUR", "GBP", "JPY", "CHF"};
    private static final String[] ACCOUNTS = {"BNY-CUST-01", "BNY-CUST-02", "BNY-CUST-03", "BNY-CUST-04"};

    public record RawTradeMessages(String tradeRef, String fixMessage, String fpmlXml, String delimitedLine) {
    }

    public record GeneratedCrossFormatStream(List<RawTradeMessages> trades, List<String> seededDisagreementTradeRefs,
                                              java.util.Map<String, SeededDisagreementField> seededFieldByTradeRef) {
    }

    private record TradeFacts(String tradeRef, String isin, long quantity, BigDecimal price,
                               LocalDate settlementDate, String currency, String account) {
    }

    /**
     * Generates {@code tradeCount} trades, each with all three raw formats
     * present. Exactly {@code seededDisagreementCount} of them have one
     * field altered in exactly one of the three raw messages, round-robining
     * across every {@link SeededDisagreementField} and every
     * {@link DisagreeingFormat} so the 30/30 claim exercises a genuine mix
     * of field types and which format carries the wrong value, not one
     * repeated case.
     */
    public GeneratedCrossFormatStream generate(int tradeCount, int seededDisagreementCount, long seed) {
        if (seededDisagreementCount > tradeCount) {
            throw new IllegalArgumentException("cannot seed more disagreements than trades");
        }
        Random random = new Random(seed);
        List<RawTradeMessages> trades = new ArrayList<>(tradeCount);
        List<String> seededRefs = new ArrayList<>(seededDisagreementCount);
        java.util.Map<String, SeededDisagreementField> seededFieldByTradeRef = new java.util.LinkedHashMap<>();

        int stride = seededDisagreementCount == 0 ? tradeCount + 1 : tradeCount / seededDisagreementCount;
        boolean[] isSeeded = new boolean[tradeCount];
        for (int i = 0; i < seededDisagreementCount; i++) {
            isSeeded[Math.min(i * stride, tradeCount - 1)] = true;
        }

        int seededSoFar = 0;
        for (int i = 0; i < tradeCount; i++) {
            TradeFacts facts = randomFacts("XTRD-%08d".formatted(i), random);

            if (isSeeded[i]) {
                SeededDisagreementField field = SeededDisagreementField.values()[seededSoFar % SeededDisagreementField.values().length];
                DisagreeingFormat wrongFormat = DisagreeingFormat.values()[seededSoFar % DisagreeingFormat.values().length];
                seededSoFar++;
                trades.add(buildWithDisagreement(facts, i, field, wrongFormat));
                seededRefs.add(facts.tradeRef());
                seededFieldByTradeRef.put(facts.tradeRef(), field);
            } else {
                trades.add(buildAgreeing(facts, i));
            }
        }

        return new GeneratedCrossFormatStream(trades, seededRefs, seededFieldByTradeRef);
    }

    private TradeFacts randomFacts(String tradeRef, Random random) {
        String isin = ISINS[random.nextInt(ISINS.length)];
        long quantity = 100L + random.nextInt(9900);
        BigDecimal price = BigDecimal.valueOf(10 + random.nextDouble() * 990).setScale(6, RoundingMode.HALF_UP);
        LocalDate settlementDate = LocalDate.of(2026, 7, 1).plusDays(random.nextInt(20));
        String currency = CURRENCIES[random.nextInt(CURRENCIES.length)];
        String account = ACCOUNTS[random.nextInt(ACCOUNTS.length)];
        return new TradeFacts(tradeRef, isin, quantity, price, settlementDate, currency, account);
    }

    private RawTradeMessages buildAgreeing(TradeFacts f, int index) {
        return new RawTradeMessages(
                f.tradeRef(),
                fix(f, index, f.isin(), f.quantity(), f.price(), f.settlementDate(), f.currency(), f.account()),
                fpml(f, index, f.isin(), f.quantity(), f.price(), f.settlementDate(), f.currency(), f.account()),
                delimited(f, index, f.isin(), f.quantity(), f.price(), f.settlementDate(), f.currency(), f.account()));
    }

    private RawTradeMessages buildWithDisagreement(TradeFacts f, int index, SeededDisagreementField field, DisagreeingFormat wrongFormat) {
        String fixIsin = f.isin(), fpmlIsin = f.isin(), delimitedIsin = f.isin();
        long fixQty = f.quantity(), fpmlQty = f.quantity(), delimitedQty = f.quantity();
        BigDecimal fixPrice = f.price(), fpmlPrice = f.price(), delimitedPrice = f.price();
        LocalDate fixDate = f.settlementDate(), fpmlDate = f.settlementDate(), delimitedDate = f.settlementDate();
        String fixCcy = f.currency(), fpmlCcy = f.currency(), delimitedCcy = f.currency();
        String fixAcct = f.account(), fpmlAcct = f.account(), delimitedAcct = f.account();

        switch (field) {
            case ISIN -> {
                String altered = ISINS[(indexOf(ISINS, f.isin()) + 1) % ISINS.length];
                switch (wrongFormat) {
                    case FIX -> fixIsin = altered;
                    case FPML -> fpmlIsin = altered;
                    case DELIMITED -> delimitedIsin = altered;
                }
            }
            case QUANTITY -> {
                long altered = f.quantity() + 1;
                switch (wrongFormat) {
                    case FIX -> fixQty = altered;
                    case FPML -> fpmlQty = altered;
                    case DELIMITED -> delimitedQty = altered;
                }
            }
            case PRICE -> {
                BigDecimal altered = f.price().multiply(BigDecimal.valueOf(1.01)).setScale(6, RoundingMode.HALF_UP);
                switch (wrongFormat) {
                    case FIX -> fixPrice = altered;
                    case FPML -> fpmlPrice = altered;
                    case DELIMITED -> delimitedPrice = altered;
                }
            }
            case SETTLEMENT_DATE -> {
                LocalDate altered = f.settlementDate().plusDays(1);
                switch (wrongFormat) {
                    case FIX -> fixDate = altered;
                    case FPML -> fpmlDate = altered;
                    case DELIMITED -> delimitedDate = altered;
                }
            }
            case CURRENCY -> {
                String altered = f.currency().equals("USD") ? "EUR" : "USD";
                switch (wrongFormat) {
                    case FIX -> fixCcy = altered;
                    case FPML -> fpmlCcy = altered;
                    case DELIMITED -> delimitedCcy = altered;
                }
            }
            case ACCOUNT -> {
                String altered = ACCOUNTS[(indexOf(ACCOUNTS, f.account()) + 1) % ACCOUNTS.length];
                switch (wrongFormat) {
                    case FIX -> fixAcct = altered;
                    case FPML -> fpmlAcct = altered;
                    case DELIMITED -> delimitedAcct = altered;
                }
            }
        }

        return new RawTradeMessages(
                f.tradeRef(),
                fix(f, index, fixIsin, fixQty, fixPrice, fixDate, fixCcy, fixAcct),
                fpml(f, index, fpmlIsin, fpmlQty, fpmlPrice, fpmlDate, fpmlCcy, fpmlAcct),
                delimited(f, index, delimitedIsin, delimitedQty, delimitedPrice, delimitedDate, delimitedCcy, delimitedAcct));
    }

    private int indexOf(String[] array, String value) {
        for (int i = 0; i < array.length; i++) {
            if (array[i].equals(value)) {
                return i;
            }
        }
        return 0;
    }

    private String fix(TradeFacts f, int index, String isin, long qty, BigDecimal price, LocalDate date, String ccy, String account) {
        return "35=AS|880=%s|70=ALLOC%08d|55=%s|38=%d|44=%s|64=%s|15=%s|79=%s".formatted(
                f.tradeRef(), index, isin, qty, price.toPlainString(), date.format(FIX_DATE), ccy, account);
    }

    private String fpml(TradeFacts f, int index, String isin, long qty, BigDecimal price, LocalDate date, String ccy, String account) {
        return """
                <tradeConfirmation>
                  <tradeReference>%s</tradeReference>
                  <instrument><isin>%s</isin></instrument>
                  <quantity>%d</quantity>
                  <price>%s</price>
                  <settlementDate>%s</settlementDate>
                  <currency>%s</currency>
                  <account>%s</account>
                  <messageId>FPML%08d</messageId>
                </tradeConfirmation>
                """.formatted(f.tradeRef(), isin, qty, price.toPlainString(), date, ccy, account, index);
    }

    private String delimited(TradeFacts f, int index, String isin, long qty, BigDecimal price, LocalDate date, String ccy, String account) {
        return "%s,%s,%d,%s,%s,%s,%s,POST%08d".formatted(
                f.tradeRef(), isin, qty, price.toPlainString(), date, ccy, account, index);
    }
}
