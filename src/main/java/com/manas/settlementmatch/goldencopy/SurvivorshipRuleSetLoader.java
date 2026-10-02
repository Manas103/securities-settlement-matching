package com.manas.settlementmatch.goldencopy;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads the survivorship ruleset out of a YAML file (classpath resource,
 * {@code goldencopy-survivorship-rules.yml}), the same pattern
 * {@code ToleranceRuleSetLoader} already uses for the matching engine's
 * tolerance rules: this is deliberately the only place in the codebase that
 * knows the file format.
 */
public final class SurvivorshipRuleSetLoader {

    private SurvivorshipRuleSetLoader() {
    }

    public static SurvivorshipRuleSet loadFromClasspath(String resourcePath) {
        try (InputStream in = SurvivorshipRuleSetLoader.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IllegalArgumentException("survivorship ruleset resource not found on classpath: " + resourcePath);
            }
            return load(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @SuppressWarnings("unchecked")
    public static SurvivorshipRuleSet load(InputStream in) {
        Yaml yaml = new Yaml();
        Map<String, Object> root = yaml.load(in);
        Map<String, Object> fields = (Map<String, Object>) root.get("fields");
        Map<GoldenField, SurvivorshipRule> rules = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : fields.entrySet()) {
            GoldenField field = GoldenField.fromFieldName(entry.getKey());
            Map<String, Object> spec = (Map<String, Object>) entry.getValue();
            List<String> precedenceNames = (List<String>) spec.getOrDefault("precedence", List.of());
            List<Vendor> precedence = new ArrayList<>();
            for (String name : precedenceNames) {
                precedence.add(Vendor.valueOf(name));
            }
            String dataOwner = (String) spec.get("dataOwner");
            if (dataOwner == null || dataOwner.isBlank()) {
                throw new IllegalArgumentException("golden field " + field.fieldName() + " has no declared data owner");
            }
            rules.put(field, new SurvivorshipRule(field, List.copyOf(precedence), dataOwner));
        }
        return new SurvivorshipRuleSet(rules);
    }
}
