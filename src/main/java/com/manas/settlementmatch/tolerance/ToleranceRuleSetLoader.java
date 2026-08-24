package com.manas.settlementmatch.tolerance;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reads the tolerance ruleset out of a YAML file (classpath resource by
 * default, {@code tolerance-rules.yml}). This is deliberately the only place
 * in the codebase that knows the file format; everything downstream works
 * against {@link ToleranceRuleSet}.
 */
public final class ToleranceRuleSetLoader {

    private ToleranceRuleSetLoader() {
    }

    public static ToleranceRuleSet loadFromClasspath(String resourcePath) {
        try (InputStream in = ToleranceRuleSetLoader.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IllegalArgumentException("tolerance ruleset resource not found on classpath: " + resourcePath);
            }
            return load(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @SuppressWarnings("unchecked")
    public static ToleranceRuleSet load(InputStream in) {
        Yaml yaml = new Yaml();
        Map<String, Object> root = yaml.load(in);
        Map<String, Object> fields = (Map<String, Object>) root.get("fields");
        Map<String, ToleranceRule> rules = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : fields.entrySet()) {
            String fieldName = entry.getKey();
            Map<String, Object> spec = (Map<String, Object>) entry.getValue();
            ComparisonType comparison = ComparisonType.valueOf((String) spec.get("comparison"));
            BigDecimal toleranceValue = BigDecimal.ZERO;
            if (spec.containsKey("toleranceBps")) {
                toleranceValue = new BigDecimal(spec.get("toleranceBps").toString());
            } else if (spec.containsKey("toleranceAbsolute")) {
                toleranceValue = new BigDecimal(spec.get("toleranceAbsolute").toString());
            }
            rules.put(fieldName, new ToleranceRule(fieldName, comparison, toleranceValue));
        }
        return new ToleranceRuleSet(rules);
    }
}
