package com.manas.settlementmatch.config;

import com.manas.settlementmatch.engine.MatchingEngine;
import com.manas.settlementmatch.gateway.CrossFormatReconciliationGate;
import com.manas.settlementmatch.tolerance.ToleranceRuleSet;
import com.manas.settlementmatch.tolerance.ToleranceRuleSetLoader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EngineConfig {

    @Bean
    public ToleranceRuleSet toleranceRuleSet(@Value("${settlement.tolerance-rules-resource:tolerance-rules.yml}") String resourcePath) {
        return ToleranceRuleSetLoader.loadFromClasspath(resourcePath);
    }

    @Bean
    public MatchingEngine matchingEngine(ToleranceRuleSet toleranceRuleSet) {
        return new MatchingEngine(toleranceRuleSet);
    }

    @Bean
    public CrossFormatReconciliationGate crossFormatReconciliationGate() {
        return new CrossFormatReconciliationGate();
    }
}
