package com.manas.settlementmatch.goldencopy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The identifier cross-reference layer: maps one underlying security across
 * CUSIP, ISIN, SEDOL and ticker, the four schemes the four vendor feeds are
 * each independently keyed on. Every identifier produced here is synthetic:
 * the CUSIP and SEDOL "base" characters, the ticker, and the internal
 * sequence number are all deterministically generated from an internal
 * index, not drawn from any real issued security. What is real is the
 * math: CUSIP, ISIN and SEDOL check digits are computed with the actual,
 * publicly documented check-digit algorithms for each scheme (validated in
 * {@code IdentifierCrosswalkTest} against Apple Inc.'s real, publicly known
 * CUSIP 037833100 and ISIN US0378331005), so every identifier here is
 * structurally valid, it is just not a real one. Tickers carry no checksum
 * in real life either, so the synthetic tickers here are plain four-letter
 * strings with no check-digit concept to borrow.
 */
public final class IdentifierCrosswalk {

    private static final String CUSIP_ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    // Real SEDOLs exclude vowels and a handful of look-alike letters; digits come first in the value table.
    private static final String SEDOL_LETTERS = "BCDFGHJKLMNPQRSTVWXYZ";
    private static final String SEDOL_ALPHABET = "0123456789" + SEDOL_LETTERS;
    private static final String TICKER_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final int[] SEDOL_WEIGHTS = {1, 3, 1, 7, 3, 9};

    private final List<SecurityIdentifiers> all;
    private final Map<String, SecurityIdentifiers> byInternalKey = new HashMap<>();
    private final Map<String, String> cusipToKey = new HashMap<>();
    private final Map<String, String> isinToKey = new HashMap<>();
    private final Map<String, String> sedolToKey = new HashMap<>();
    private final Map<String, String> tickerToKey = new HashMap<>();

    public IdentifierCrosswalk(int count) {
        long tickerCapacity = ipow(TICKER_ALPHABET.length(), 4);
        if (count > tickerCapacity) {
            throw new IllegalArgumentException(
                    "the 4-letter synthetic ticker alphabet cannot address " + count + " distinct securities (capacity " + tickerCapacity + ")");
        }
        List<SecurityIdentifiers> generated = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            SecurityIdentifiers ids = generateOne(i);
            generated.add(ids);
            byInternalKey.put(ids.internalKey(), ids);
            cusipToKey.put(ids.cusip(), ids.internalKey());
            isinToKey.put(ids.isin(), ids.internalKey());
            sedolToKey.put(ids.sedol(), ids.internalKey());
            tickerToKey.put(ids.ticker(), ids.internalKey());
        }
        this.all = generated;
    }

    private static long ipow(long base, int exp) {
        long r = 1;
        for (int i = 0; i < exp; i++) {
            r *= base;
        }
        return r;
    }

    private SecurityIdentifiers generateOne(int index) {
        String internalKey = "SEC-%08d".formatted(index);

        String cusipBase = encodeBaseN(index, CUSIP_ALPHABET, 8);
        String cusip = cusipBase + cusipCheckDigit(cusipBase);

        // US ISINs genuinely use the 9-character CUSIP as their NSIN; reusing that relationship
        // here is accurate to the real scheme, not an additional synthetic shortcut.
        String isinBody = "US" + cusip;
        String isin = isinBody + isinCheckDigit(isinBody);

        String sedolBase = encodeBaseN(index, SEDOL_ALPHABET, 6);
        String sedol = sedolBase + sedolCheckDigit(sedolBase);

        String ticker = encodeBaseN(index, TICKER_ALPHABET, 4);

        return new SecurityIdentifiers(internalKey, cusip, isin, sedol, ticker);
    }

    private static String encodeBaseN(long value, String alphabet, int length) {
        int base = alphabet.length();
        char[] out = new char[length];
        long v = value;
        for (int i = length - 1; i >= 0; i--) {
            out[i] = alphabet.charAt((int) (v % base));
            v /= base;
        }
        return new String(out);
    }

    /** The real, publicly documented CUSIP check-digit algorithm: modulus 10, double every second character. */
    public static int cusipCheckDigit(String base8) {
        int sum = 0;
        for (int i = 0; i < base8.length(); i++) {
            int v = cusipCharValue(base8.charAt(i));
            if (i % 2 == 1) {
                v *= 2;
            }
            sum += v / 10 + v % 10;
        }
        return (10 - (sum % 10)) % 10;
    }

    private static int cusipCharValue(char c) {
        if (Character.isDigit(c)) {
            return c - '0';
        }
        return (c - 'A') + 10;
    }

    /** The real ISIN check-digit algorithm: expand letters to two-digit numerals, then a Luhn pass. */
    public static int isinCheckDigit(String body) {
        StringBuilder digits = new StringBuilder();
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (Character.isDigit(c)) {
                digits.append(c);
            } else {
                digits.append((c - 'A') + 10);
            }
        }
        int sum = 0;
        boolean doubleIt = true;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int d = digits.charAt(i) - '0';
            if (doubleIt) {
                d *= 2;
                if (d > 9) {
                    d -= 9;
                }
            }
            sum += d;
            doubleIt = !doubleIt;
        }
        return (10 - (sum % 10)) % 10;
    }

    /** The real SEDOL check-digit algorithm: weighted sum over the six-character body, modulus 10. */
    public static int sedolCheckDigit(String base6) {
        int sum = 0;
        for (int i = 0; i < 6; i++) {
            sum += sedolCharValue(base6.charAt(i)) * SEDOL_WEIGHTS[i];
        }
        return (10 - (sum % 10)) % 10;
    }

    private static int sedolCharValue(char c) {
        if (Character.isDigit(c)) {
            return c - '0';
        }
        return 10 + SEDOL_LETTERS.indexOf(c);
    }

    public Optional<String> resolveToInternalKey(IdentifierScheme scheme, String value) {
        Map<String, String> index = switch (scheme) {
            case CUSIP -> cusipToKey;
            case ISIN -> isinToKey;
            case SEDOL -> sedolToKey;
            case TICKER -> tickerToKey;
        };
        return Optional.ofNullable(index.get(value));
    }

    public Optional<SecurityIdentifiers> identifiersFor(String internalKey) {
        return Optional.ofNullable(byInternalKey.get(internalKey));
    }

    public List<SecurityIdentifiers> all() {
        return all;
    }

    public int size() {
        return all.size();
    }
}
