package com.manas.settlementmatch.service;

import com.manas.settlementmatch.gateway.CrossFormatReconciliationGate;
import com.manas.settlementmatch.gateway.FieldDisagreement;
import com.manas.settlementmatch.gateway.GateOutcome;
import com.manas.settlementmatch.gateway.NormalizedTradeRecord;
import com.manas.settlementmatch.gateway.QuarantineResult;
import com.manas.settlementmatch.model.QuarantinedFieldDisagreementEmbeddable;
import com.manas.settlementmatch.model.QuarantinedTradeEntity;
import com.manas.settlementmatch.repository.QuarantinedTradeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Wires the pure {@link CrossFormatReconciliationGate} to persistence, the
 * same split {@link SettlementMatchingService} uses for the existing
 * matching engine: the gate itself has no JPA dependency (it is
 * unit-testable and reusable in the reference-oracle diff test without a
 * Spring context or a database), and this service is the only place a
 * quarantined trade becomes a database row. A reconciled trade is not
 * persisted here; it is handed to the existing settlement pipeline as a
 * normal leg (see {@link com.manas.settlementmatch.gateway.NormalizedTradeRecordMapper}),
 * which already has its own persistence path for matches and breaks.
 */
@Service
public class CrossFormatGatewayService {

    private static final Logger log = LoggerFactory.getLogger(CrossFormatGatewayService.class);

    private final CrossFormatReconciliationGate gate;
    private final QuarantinedTradeRepository quarantinedTradeRepository;

    public CrossFormatGatewayService(CrossFormatReconciliationGate gate, QuarantinedTradeRepository quarantinedTradeRepository) {
        this.gate = gate;
        this.quarantinedTradeRepository = quarantinedTradeRepository;
    }

    @Transactional
    public GateOutcome process(NormalizedTradeRecord record) {
        GateOutcome outcome = gate.ingest(record);
        switch (outcome.type()) {
            case RECONCILED -> log.debug("reconciled across formats: tradeRef={}", outcome.reconciledTrade().tradeRef());
            case QUARANTINED -> persistQuarantine(outcome.quarantineResult());
            case BUFFERED -> log.debug("buffered, awaiting remaining formats: tradeRef={}", record.tradeRef());
            case DUPLICATE -> log.debug("duplicate source message ignored: sourceMessageId={}", record.sourceMessageId());
        }
        return outcome;
    }

    private void persistQuarantine(QuarantineResult result) {
        List<QuarantinedFieldDisagreementEmbeddable> disagreements = result.disagreements().stream()
                .map(this::toEmbeddable)
                .toList();
        quarantinedTradeRepository.save(new QuarantinedTradeEntity(result.tradeRef(), Instant.now(), disagreements));
    }

    private QuarantinedFieldDisagreementEmbeddable toEmbeddable(FieldDisagreement disagreement) {
        return new QuarantinedFieldDisagreementEmbeddable(
                disagreement.fieldName(),
                disagreement.fixValue(),
                disagreement.fpmlValue(),
                disagreement.delimitedValue());
    }
}
