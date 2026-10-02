package com.manas.settlementmatch.goldencopy;

import com.manas.settlementmatch.generator.VendorFeedGenerator;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Builds and serves the golden-copy security master in memory. Unlike the
 * settlement matching engine's breaks and matched positions, golden
 * security records are not persisted to PostgreSQL in this version; they
 * are recomputed from the four vendor feeds once at application startup.
 * This is a deliberate, disclosed scope choice (see the README's honest
 * framing), not an oversight: the resume claim is that the REST API serves
 * the consolidated record and its lineage, which an in-memory
 * consolidation already does correctly and is trivially rebuilt from the
 * same feeds.
 */
@Service
public class GoldenCopySecurityService {

    private final int demoSecurityCount;
    private final double missingnessRate;
    private final double disagreementRate;
    private final long seed;

    private IdentifierCrosswalk crosswalk;
    private SurvivorshipRuleSet ruleSet;
    private List<GoldenSecurityRecord> goldenRecords;
    private Map<String, GoldenSecurityRecord> byInternalKey;

    public GoldenCopySecurityService(
            @Value("${golden-copy.demo-security-count:25}") int demoSecurityCount,
            @Value("${golden-copy.demo-missingness-rate:0.1}") double missingnessRate,
            @Value("${golden-copy.demo-disagreement-rate:0.3}") double disagreementRate,
            @Value("${golden-copy.demo-seed:20260701}") long seed) {
        this.demoSecurityCount = demoSecurityCount;
        this.missingnessRate = missingnessRate;
        this.disagreementRate = disagreementRate;
        this.seed = seed;
    }

    @PostConstruct
    void buildDemoGoldenCopy() {
        this.crosswalk = new IdentifierCrosswalk(demoSecurityCount);
        this.ruleSet = SurvivorshipRuleSetLoader.loadFromClasspath("goldencopy-survivorship-rules.yml");
        VendorFeedGenerator generator = new VendorFeedGenerator();
        Map<Vendor, List<VendorFeedRecord>> feeds = generator.generate(crosswalk, missingnessRate, disagreementRate, seed).feedsByVendor();
        GoldenCopyConsolidationEngine engine = new GoldenCopyConsolidationEngine(crosswalk, ruleSet);
        this.goldenRecords = engine.consolidate(feeds);
        this.byInternalKey = new HashMap<>();
        for (GoldenSecurityRecord record : goldenRecords) {
            byInternalKey.put(record.internalKey(), record);
        }
    }

    public Optional<GoldenSecurityRecord> findBySchemeAndIdentifier(IdentifierScheme scheme, String identifierValue) {
        return crosswalk.resolveToInternalKey(scheme, identifierValue)
                .flatMap(key -> Optional.ofNullable(byInternalKey.get(key)));
    }

    public List<GoldenSecurityRecord> all() {
        return goldenRecords;
    }

    public SurvivorshipRuleSet ruleSet() {
        return ruleSet;
    }
}
