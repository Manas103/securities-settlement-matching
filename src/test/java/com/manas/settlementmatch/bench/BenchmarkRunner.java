package com.manas.settlementmatch.bench;

import com.manas.settlementmatch.engine.BreakResult;
import com.manas.settlementmatch.engine.MatchedPositionResult;
import com.manas.settlementmatch.engine.MatchingEngine;
import com.manas.settlementmatch.generator.NoisyStreamBuilder;
import com.manas.settlementmatch.generator.SyntheticInstructionGenerator;
import com.manas.settlementmatch.kafka.SettlementInstructionJson;
import com.manas.settlementmatch.model.SettlementInstruction;
import com.manas.settlementmatch.tolerance.ToleranceRuleSet;
import com.manas.settlementmatch.tolerance.ToleranceRuleSetLoader;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.test.EmbeddedKafkaKraftBroker;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The real measurement run behind claims 3 through 6 of the resume bullet.
 * Not part of the default {@code mvn test} run (tagged {@code benchmark},
 * excluded by the surefire configuration in {@code pom.xml}); run
 * explicitly with {@code mvn test -Dtest=BenchmarkRunner -DexcludedGroups=}.
 *
 * <p>Uses a real, in-process Kafka broker (spring-kafka-test's
 * {@code EmbeddedKafkaKraftBroker}, KRaft mode, not a mock producer/
 * consumer) for both the canonical 100,000-message run and the noisy
 * replay. See the README's "honest framing" section for exactly what this
 * does and does not claim about production Kafka.
 */
class BenchmarkRunner {

    private static final int PAIR_COUNT = 50_000; // 100,000 instructions total
    private static final int SEEDED_BREAK_COUNT = 48;
    private static final long GENERATOR_SEED = 20260724L;
    private static final long NOISE_SEED = 918273645L;

    @Test
    @Tag("benchmark")
    void measureFullSyntheticStreamOverEmbeddedKafka() throws Exception {
        StringBuilder report = new StringBuilder();
        line(report, "=== Securities Settlement Matching -- benchmark run ===");
        line(report, "started: " + Instant.now());

        ToleranceRuleSet ruleSet = ToleranceRuleSetLoader.loadFromClasspath("tolerance-rules.yml");
        SyntheticInstructionGenerator generator = new SyntheticInstructionGenerator();
        SyntheticInstructionGenerator.GeneratedStream stream = generator.generate(PAIR_COUNT, SEEDED_BREAK_COUNT, GENERATOR_SEED);

        line(report, "");
        line(report, "-- dataset --");
        line(report, "trade-reference pairs: " + PAIR_COUNT);
        line(report, "total instructions: " + stream.instructions().size());
        line(report, "seeded breaks: " + stream.seededBreakTradeRefs().size());

        EmbeddedKafkaKraftBroker broker = new EmbeddedKafkaKraftBroker(1, 4,
                "bench-canonical", "bench-noisy");
        broker.afterPropertiesSet();
        try {
            String bootstrapServers = broker.getBrokersAsString();

            line(report, "");
            line(report, "-- canonical run (in order, no duplicates) --");
            long t0 = System.nanoTime();
            produce(bootstrapServers, "bench-canonical", stream.instructions());
            MatchingEngine canonicalEngine = new MatchingEngine(ruleSet);
            consumeInto(bootstrapServers, "bench-canonical", "bench-group-canonical", stream.instructions().size(), canonicalEngine);
            long canonicalMillis = (System.nanoTime() - t0) / 1_000_000;

            List<MatchedPositionResult> canonicalMatched = canonicalEngine.matchedPositions();
            List<BreakResult> canonicalBreaks = canonicalEngine.breaks();

            Set<String> seededRefs = new HashSet<>(stream.seededBreakTradeRefs());
            Set<String> caughtBreakRefs = canonicalBreaks.stream().map(BreakResult::tradeRef).collect(Collectors.toSet());
            long breaksCaught = seededRefs.stream().filter(caughtBreakRefs::contains).count();
            long falseMatches = caughtBreakRefs.stream().filter(ref -> !seededRefs.contains(ref)).count();

            line(report, "elapsed: " + canonicalMillis + " ms");
            line(report, "matched positions: " + canonicalMatched.size());
            line(report, "breaks filed: " + canonicalBreaks.size());
            line(report, "seeded breaks caught: " + breaksCaught + " / " + SEEDED_BREAK_COUNT);
            line(report, "false matches (breaks filed on an un-seeded, genuinely matching pair): " + falseMatches);
            line(report, "messages still awaiting a counterpart at end of run: " + canonicalEngine.stillAwaitingCounterpart());
            line(report, "distinct message ids seen: " + canonicalEngine.distinctMessageIdsSeen());

            line(report, "");
            line(report, "-- break field-name breakdown (which field type each seeded break was caught on) --");
            for (BreakResult b : canonicalBreaks) {
                if (seededRefs.contains(b.tradeRef())) {
                    line(report, b.tradeRef() + ": " + b.fieldNames());
                }
            }

            String canonicalDigest = digestFinalState(canonicalMatched, canonicalBreaks);
            line(report, "");
            line(report, "canonical final-state digest (SHA-256): " + canonicalDigest);

            line(report, "");
            line(report, "-- noisy replay (5% duplicated, fully shuffled out of trade-reference order) --");
            List<SettlementInstruction> noisy = NoisyStreamBuilder.buildNoisyStream(stream.instructions(), 0.05, NOISE_SEED);
            line(report, "noisy stream size: " + noisy.size() + " (" + (noisy.size() - stream.instructions().size()) + " duplicated deliveries)");

            long t1 = System.nanoTime();
            produce(bootstrapServers, "bench-noisy", noisy);
            MatchingEngine noisyEngine = new MatchingEngine(ruleSet);
            consumeInto(bootstrapServers, "bench-noisy", "bench-group-noisy", noisy.size(), noisyEngine);
            long noisyMillis = (System.nanoTime() - t1) / 1_000_000;

            List<MatchedPositionResult> noisyMatched = noisyEngine.matchedPositions();
            List<BreakResult> noisyBreaks = noisyEngine.breaks();
            String noisyDigest = digestFinalState(noisyMatched, noisyBreaks);

            line(report, "elapsed: " + noisyMillis + " ms");
            line(report, "matched positions: " + noisyMatched.size());
            line(report, "breaks filed: " + noisyBreaks.size());
            line(report, "distinct message ids seen (post-dedup): " + noisyEngine.distinctMessageIdsSeen());
            line(report, "noisy final-state digest (SHA-256): " + noisyDigest);

            boolean identical = canonicalDigest.equals(noisyDigest);
            line(report, "");
            line(report, "identical final state after noisy replay: " + identical);

            line(report, "");
            line(report, "finished: " + Instant.now());

            System.out.println(report);

            assertThat(canonicalMatched.size() + canonicalBreaks.size()).isEqualTo(PAIR_COUNT);
            assertThat(noisyEngine.distinctMessageIdsSeen()).isEqualTo(stream.instructions().size());
            assertThat(identical).isTrue();
        } finally {
            broker.destroy();
        }
    }

    private void line(StringBuilder sb, String text) {
        sb.append(text).append(System.lineSeparator());
    }

    private void produce(String bootstrapServers, String topic, List<SettlementInstruction> instructions) {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
            for (SettlementInstruction instruction : instructions) {
                producer.send(new ProducerRecord<>(topic, instruction.tradeRef(), SettlementInstructionJson.toJson(instruction)));
            }
            producer.flush();
        }
    }

    private void consumeInto(String bootstrapServers, String topic, String groupId, int expectedCount, MatchingEngine engine) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(Collections.singletonList(topic));
            int consumed = 0;
            long deadline = System.nanoTime() + Duration.ofSeconds(120).toNanos();
            while (consumed < expectedCount && System.nanoTime() < deadline) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
                for (ConsumerRecord<String, String> record : records) {
                    engine.ingest(SettlementInstructionJson.fromJson(record.value()));
                    consumed++;
                }
            }
            if (consumed < expectedCount) {
                throw new IllegalStateException("consumed only " + consumed + " of " + expectedCount + " expected records from " + topic);
            }
        }
    }

    private String digestFinalState(List<MatchedPositionResult> matched, List<BreakResult> breaks) {
        List<String> matchedLines = matched.stream()
                .map(m -> "MATCH|" + m.tradeRef() + "|" + m.partyA().isin() + "|" + m.partyA().quantity()
                        + "|" + m.partyA().price().toPlainString() + "|" + m.partyA().settlementDate() + "|" + m.partyA().currency())
                .sorted()
                .toList();
        List<String> breakLines = breaks.stream()
                .map(b -> "BREAK|" + b.tradeRef() + "|" + b.fieldNames())
                .sorted()
                .toList();

        List<String> all = new ArrayList<>(matchedLines.size() + breakLines.size());
        all.addAll(matchedLines);
        all.addAll(breakLines);
        String canonicalSerialization = String.join("\n", all);

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonicalSerialization.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
