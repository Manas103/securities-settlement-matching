package com.manas.settlementmatch.goldencopy;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class IdentifierCrosswalkTest {

    /**
     * Apple Inc.'s real, publicly known CUSIP (037833100) and ISIN
     * (US0378331005) are used only to validate the check-digit algorithms
     * against a real, known-correct answer; this project does not produce
     * or claim any real issued CUSIP/ISIN/SEDOL anywhere else.
     */
    @Test
    void cusipAndIsinCheckDigitsMatchAppleIncsRealPublicIdentifiers() {
        assertThat(IdentifierCrosswalk.cusipCheckDigit("03783310")).isEqualTo(0);
        assertThat(IdentifierCrosswalk.isinCheckDigit("US037833100")).isEqualTo(5);
    }

    @Test
    void everyGeneratedIdentifierPassesItsOwnCheckDigit() {
        IdentifierCrosswalk crosswalk = new IdentifierCrosswalk(500);
        for (SecurityIdentifiers ids : crosswalk.all()) {
            String cusipBase = ids.cusip().substring(0, 8);
            int cusipCheck = Character.getNumericValue(ids.cusip().charAt(8));
            assertThat(IdentifierCrosswalk.cusipCheckDigit(cusipBase)).isEqualTo(cusipCheck);

            String isinBody = ids.isin().substring(0, 11);
            int isinCheck = Character.getNumericValue(ids.isin().charAt(11));
            assertThat(IdentifierCrosswalk.isinCheckDigit(isinBody)).isEqualTo(isinCheck);

            String sedolBase = ids.sedol().substring(0, 6);
            int sedolCheck = Character.getNumericValue(ids.sedol().charAt(6));
            assertThat(IdentifierCrosswalk.sedolCheckDigit(sedolBase)).isEqualTo(sedolCheck);
        }
    }

    @Test
    void allFourSchemesAreUniqueAcrossFiveHundredSecurities() {
        IdentifierCrosswalk crosswalk = new IdentifierCrosswalk(500);
        Set<String> cusips = new HashSet<>();
        Set<String> isins = new HashSet<>();
        Set<String> sedols = new HashSet<>();
        Set<String> tickers = new HashSet<>();
        for (SecurityIdentifiers ids : crosswalk.all()) {
            assertThat(cusips.add(ids.cusip())).isTrue();
            assertThat(isins.add(ids.isin())).isTrue();
            assertThat(sedols.add(ids.sedol())).isTrue();
            assertThat(tickers.add(ids.ticker())).isTrue();
        }
    }

    @Test
    void everySchemeResolvesBackToTheSameInternalKey() {
        IdentifierCrosswalk crosswalk = new IdentifierCrosswalk(10);
        SecurityIdentifiers ids = crosswalk.all().get(3);
        for (IdentifierScheme scheme : IdentifierScheme.values()) {
            assertThat(crosswalk.resolveToInternalKey(scheme, ids.forScheme(scheme))).contains(ids.internalKey());
        }
    }

    @Test
    void unknownIdentifierDoesNotResolve() {
        IdentifierCrosswalk crosswalk = new IdentifierCrosswalk(10);
        assertThat(crosswalk.resolveToInternalKey(IdentifierScheme.CUSIP, "NOTAREALCUSIP")).isEmpty();
    }
}
