package com.manas.settlementmatch.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.manas.settlementmatch.model.SettlementInstruction;

import java.io.UncheckedIOException;

/**
 * The single shared JSON mapping for {@link SettlementInstruction}, used
 * both by the Spring-managed Kafka (de)serializers in {@link KafkaConfig}
 * and, directly, by the benchmark runner's raw {@code KafkaProducer}/
 * {@code KafkaConsumer} (which has no Spring context to inject a bean
 * from). One mapping, one place {@code LocalDate}/{@code BigDecimal}
 * handling can go wrong.
 */
public final class SettlementInstructionJson {

    public static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private SettlementInstructionJson() {
    }

    public static String toJson(SettlementInstruction instruction) {
        try {
            return MAPPER.writeValueAsString(instruction);
        } catch (Exception e) {
            throw new UncheckedIOException(new java.io.IOException(e));
        }
    }

    public static SettlementInstruction fromJson(String json) {
        try {
            return MAPPER.readValue(json, SettlementInstruction.class);
        } catch (Exception e) {
            throw new UncheckedIOException(new java.io.IOException(e));
        }
    }
}
