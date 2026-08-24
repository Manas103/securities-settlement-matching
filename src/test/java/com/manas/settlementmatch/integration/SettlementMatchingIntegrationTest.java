package com.manas.settlementmatch.integration;

import com.manas.settlementmatch.generator.SyntheticInstructionGenerator;
import com.manas.settlementmatch.model.SettlementInstruction;
import com.manas.settlementmatch.repository.BreakRepository;
import com.manas.settlementmatch.repository.MatchedPositionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end: a real (embedded) Kafka broker, the real
 * {@code @KafkaListener}, the real matching engine, and a real H2
 * database in PostgreSQL-compatibility mode, all wired through the actual
 * Spring context. Kept to a modest sample (500 pairs); the full
 * 100,000-message measurement run lives in {@code bench.BenchmarkRunner}
 * and is not part of the default test suite.
 */
@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = {"settlement-instructions-test"})
class SettlementMatchingIntegrationTest {

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", () -> System.getProperty("spring.embedded.kafka.brokers"));
    }

    @Autowired
    private KafkaTemplate<String, SettlementInstruction> kafkaTemplate;

    @Autowired
    private MatchedPositionRepository matchedPositionRepository;

    @Autowired
    private BreakRepository breakRepository;

    @Test
    void endToEndThroughRealKafkaAndH2() {
        SyntheticInstructionGenerator generator = new SyntheticInstructionGenerator();
        var stream = generator.generate(500, 10, 7L);

        for (SettlementInstruction instruction : stream.instructions()) {
            kafkaTemplate.send("settlement-instructions-test", instruction.tradeRef(), instruction);
        }
        kafkaTemplate.flush();

        await().atMost(60, TimeUnit.SECONDS).untilAsserted(() -> {
            long totalRows = matchedPositionRepository.count() + breakRepository.count();
            assertThat(totalRows).isEqualTo(500);
        });

        assertThat(breakRepository.count()).isEqualTo(10);
        assertThat(matchedPositionRepository.count()).isEqualTo(490);

        for (String seededRef : stream.seededBreakTradeRefs()) {
            assertThat(breakRepository.findByTradeRef(seededRef)).isPresent();
        }
    }
}
