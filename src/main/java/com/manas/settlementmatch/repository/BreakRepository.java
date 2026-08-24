package com.manas.settlementmatch.repository;

import com.manas.settlementmatch.model.BreakEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BreakRepository extends JpaRepository<BreakEntity, Long> {
    Optional<BreakEntity> findByTradeRef(String tradeRef);
}
