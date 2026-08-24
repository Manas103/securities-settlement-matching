package com.manas.settlementmatch.kafka;

import com.manas.settlementmatch.model.SettlementInstruction;
import com.manas.settlementmatch.service.SettlementMatchingService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes settlement instruction messages off the configured Kafka topic
 * and hands each one to {@link SettlementMatchingService}. This is the only
 * place a raw Kafka record becomes a matching decision; everything else is
 * plain, Kafka-unaware Java.
 */
@Component
public class InstructionListener {

    private final SettlementMatchingService service;

    public InstructionListener(SettlementMatchingService service) {
        this.service = service;
    }

    @KafkaListener(
            topics = "${settlement.kafka.topic:settlement-instructions}",
            containerFactory = "kafkaListenerContainerFactory")
    public void onMessage(SettlementInstruction instruction) {
        service.process(instruction);
    }
}
