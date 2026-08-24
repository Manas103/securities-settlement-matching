package com.manas.settlementmatch.repository;

import com.manas.settlementmatch.model.MatchedPositionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MatchedPositionRepository extends JpaRepository<MatchedPositionEntity, Long> {
    Optional<MatchedPositionEntity> findByTradeRef(String tradeRef);
    long countByTradeRefIn(java.util.Collection<String> tradeRefs);
}
