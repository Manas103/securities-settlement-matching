package com.manas.settlementmatch.goldencopy;

import com.manas.settlementmatch.generator.VendorFeedGenerator;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The real engine vs. the deliberately slow, deliberately unindexed oracle,
 * diffed exactly, the same discipline {@code ReferenceOracleDiffTest} and
 * {@code ReferenceOracleReconciliationGateDiffTest} already follow in this
 * repository. Kept to a modest sample (400 securities) since the oracle is
 * O(n^2)-ish; the full 250,000-record run lives in the tagged
 * {@code bench.GoldenCopyBenchmarkRunner} and is not part of this default test.
 */
class GoldenCopyReferenceOracleDiffTest {

    @Test
    void fastEngineAndReferenceOracleAgreeExactlyOverFourHundredSecurities() {
        int sampleSize = 400;
        SurvivorshipRuleSet ruleSet = SurvivorshipRuleSetLoader.loadFromClasspath("goldencopy-survivorship-rules.yml");
        IdentifierCrosswalk crosswalk = new IdentifierCrosswalk(sampleSize);
        VendorFeedGenerator generator = new VendorFeedGenerator();
        Map<Vendor, List<VendorFeedRecord>> feeds = generator.generate(crosswalk, 0.08, 0.12, 20260601L).feedsByVendor();

        GoldenCopyConsolidationEngine engine = new GoldenCopyConsolidationEngine(crosswalk, ruleSet);
        GoldenCopyReferenceOracle oracle = new GoldenCopyReferenceOracle(crosswalk, ruleSet);

        List<GoldenSecurityRecord> fast = engine.consolidate(feeds);
        List<GoldenSecurityRecord> slow = oracle.consolidate(feeds);

        System.out.println("golden-copy reference-oracle diff: sample size = " + sampleSize
                + ", fast engine securities = " + fast.size() + ", oracle securities = " + slow.size());

        assertThat(fast).hasSize(sampleSize);
        assertThat(slow).hasSize(sampleSize);

        Map<String, GoldenSecurityRecord> fastByKey = new HashMap<>();
        for (GoldenSecurityRecord record : fast) {
            fastByKey.put(record.internalKey(), record);
        }

        for (GoldenSecurityRecord oracleRecord : slow) {
            GoldenSecurityRecord fastRecord = fastByKey.get(oracleRecord.internalKey());
            assertThat(fastRecord).isNotNull();
            for (GoldenField field : GoldenField.values()) {
                FieldResolution fastResolution = fastRecord.resolutionFor(field);
                FieldResolution oracleResolution = oracleRecord.resolutionFor(field);
                assertThat(fastResolution.outcome()).isEqualTo(oracleResolution.outcome());
                assertThat(fastResolution.value()).isEqualTo(oracleResolution.value());
                assertThat(fastResolution.sourceVendor()).isEqualTo(oracleResolution.sourceVendor());
                assertThat(fastResolution.sourceVendorRecordId()).isEqualTo(oracleResolution.sourceVendorRecordId());
                assertThat(fastResolution.heldForOwner()).isEqualTo(oracleResolution.heldForOwner());
            }
        }

        System.out.println("golden-copy reference-oracle diff: exact agreement = true");
    }
}
