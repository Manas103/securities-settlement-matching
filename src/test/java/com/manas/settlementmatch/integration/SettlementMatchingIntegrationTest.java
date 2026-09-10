package com.manas.settlementmatch.integration;

import com.manas.settlementmatch.gateway.DelimitedPostTradeFileParser;
import com.manas.settlementmatch.gateway.FixAllocationMessageParser;
import com.manas.settlementmatch.gateway.FpmlConfirmationParser;
import com.manas.settlementmatch.gateway.GateOutcome;
import com.manas.settlementmatch.gateway.NormalizedTradeRecord;
import com.manas.settlementmatch.generator.CrossFormatTradeGenerator;
import com.manas.settlementmatch.generator.SyntheticInstructionGenerator;
import com.manas.settlementmatch.model.QuarantinedTradeEntity;
import com.manas.settlementmatch.model.SettlementInstruction;
import com.manas.settlementmatch.repository.BreakRepository;
import com.manas.settlementmatch.repository.MatchedPositionRepository;
import com.manas.settlementmatch.repository.QuarantinedTradeRepository;
import com.manas.settlementmatch.service.CrossFormatGatewayService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;
import java.util.Optional;
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

    @Autowired
    private CrossFormatGatewayService crossFormatGatewayService;

    @Autowired
    private QuarantinedTradeRepository quarantinedTradeRepository;

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

    /**
     * The cross-format gateway does not go through Kafka (see the gateway
     * README section on why); this test exercises the real Spring context's
     * JPA wiring directly: a trade with a genuine field disagreement across
     * its FIX, FpML-style, and delimited descriptions is quarantined and
     * persisted as a {@link QuarantinedTradeEntity} naming the disagreeing
     * field, through the real H2 database in the same Spring context as the
     * Kafka test above.
     */
    @Test
    void crossFormatGatewayPersistsAQuarantinedTradeThroughRealH2() {
        FixAllocationMessageParser fixParser = new FixAllocationMessageParser();
        FpmlConfirmationParser fpmlParser = new FpmlConfirmationParser();
        DelimitedPostTradeFileParser delimitedParser = new DelimitedPostTradeFileParser();

        CrossFormatTradeGenerator generator = new CrossFormatTradeGenerator();
        var stream = generator.generate(1, 1, 606L);
        CrossFormatTradeGenerator.RawTradeMessages raw = stream.trades().get(0);
        String tradeRef = raw.tradeRef();

        NormalizedTradeRecord fix = fixParser.parse(raw.fixMessage());
        NormalizedTradeRecord fpml = fpmlParser.parse(raw.fpmlXml());
        NormalizedTradeRecord delimited = delimitedParser.parse(raw.delimitedLine());

        crossFormatGatewayService.process(fix);
        crossFormatGatewayService.process(fpml);
        GateOutcome outcome = crossFormatGatewayService.process(delimited);

        assertThat(outcome.type()).isEqualTo(GateOutcome.Type.QUARANTINED);

        List<QuarantinedTradeEntity> persisted = quarantinedTradeRepository.findAll();
        Optional<QuarantinedTradeEntity> match = persisted.stream()
                .filter(entity -> entity.getTradeRef().equals(tradeRef))
                .findFirst();
        assertThat(match).isPresent();
        assertThat(match.get().getDisagreements()).isNotEmpty();
        String expectedField = stream.seededFieldByTradeRef().get(tradeRef).canonicalFieldName();
        assertThat(match.get().getDisagreements())
                .anyMatch(d -> d.getFieldName().equals(expectedField));
    }
}
