package com.manas.settlementmatch.service;

import com.manas.settlementmatch.engine.BreakResult;
import com.manas.settlementmatch.engine.MatchOutcome;
import com.manas.settlementmatch.engine.MatchedPositionResult;
import com.manas.settlementmatch.engine.MatchingEngine;
import com.manas.settlementmatch.model.BreakEntity;
import com.manas.settlementmatch.model.BreakFieldDiscrepancyEmbeddable;
import com.manas.settlementmatch.model.MatchedPositionEntity;
import com.manas.settlementmatch.model.SettlementInstruction;
import com.manas.settlementmatch.repository.BreakRepository;
import com.manas.settlementmatch.repository.MatchedPositionRepository;
import com.manas.settlementmatch.tolerance.FieldDiscrepancy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Wires the pure {@link MatchingEngine} to persistence. The engine itself
 * has no JPA dependency (it is unit-testable and reusable in the benchmark
 * runner without a Spring context or a database); this service is the only
 * place a matched position or a break becomes a database row.
 */
@Service
public class SettlementMatchingService {

    private static final Logger log = LoggerFactory.getLogger(SettlementMatchingService.class);

    private final MatchingEngine engine;
    private final MatchedPositionRepository matchedPositionRepository;
    private final BreakRepository breakRepository;

    public SettlementMatchingService(MatchingEngine engine,
                                      MatchedPositionRepository matchedPositionRepository,
                                      BreakRepository breakRepository) {
        this.engine = engine;
        this.matchedPositionRepository = matchedPositionRepository;
        this.breakRepository = breakRepository;
    }

    @Transactional
    public MatchOutcome process(SettlementInstruction instruction) {
        MatchOutcome outcome = engine.ingest(instruction);
        switch (outcome.type()) {
            case MATCHED -> persistMatch(outcome.matchedPosition());
            case BREAK -> persistBreak(outcome.breakResult());
            case BUFFERED -> log.debug("buffered, awaiting counterpart: tradeRef={}", instruction.tradeRef());
            case DUPLICATE -> log.debug("duplicate message id ignored: messageId={}", instruction.messageId());
        }
        return outcome;
    }

    private void persistMatch(MatchedPositionResult position) {
        MatchedPositionEntity entity = new MatchedPositionEntity(
                position.tradeRef(),
                position.partyA().isin(),
                position.partyA().quantity(),
                position.partyA().price(),
                position.partyA().settlementDate(),
                position.partyA().currency(),
                position.partyA().counterparty(),
                position.partyB().counterparty(),
                Instant.now());
        matchedPositionRepository.save(entity);
    }

    private void persistBreak(BreakResult breakResult) {
        List<BreakFieldDiscrepancyEmbeddable> discrepancies = breakResult.discrepancies().stream()
                .map(this::toEmbeddable)
                .toList();
        breakRepository.save(new BreakEntity(breakResult.tradeRef(), Instant.now(), discrepancies));
    }

    private BreakFieldDiscrepancyEmbeddable toEmbeddable(FieldDiscrepancy discrepancy) {
        return new BreakFieldDiscrepancyEmbeddable(
                discrepancy.fieldName(),
                discrepancy.partyAValue(),
                discrepancy.partyBValue(),
                discrepancy.delta(),
                discrepancy.toleranceDescription());
    }
}
