package com.manas.settlementmatch.repository;

import com.manas.settlementmatch.model.QuarantinedTradeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface QuarantinedTradeRepository extends JpaRepository<QuarantinedTradeEntity, Long> {
    Optional<QuarantinedTradeEntity> findByTradeRef(String tradeRef);
}
