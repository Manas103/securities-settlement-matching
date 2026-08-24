package com.manas.settlementmatch.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A filed break: two sides of a settlement instruction were both present
 * but at least one field disagreed beyond its configured tolerance. Every
 * disagreeing field is named individually in {@link #discrepancies}, per
 * claim 2 ("files every unmatched item as a break naming the exact fields
 * that disagreed").
 */
@Entity
@Table(name = "settlement_break")
public class BreakEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trade_ref", nullable = false)
    private String tradeRef;

    @Column(name = "filed_at", nullable = false)
    private Instant filedAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "break_discrepancy", joinColumns = @JoinColumn(name = "break_id"))
    private List<BreakFieldDiscrepancyEmbeddable> discrepancies = new ArrayList<>();

    protected BreakEntity() {
        // JPA
    }

    public BreakEntity(String tradeRef, Instant filedAt, List<BreakFieldDiscrepancyEmbeddable> discrepancies) {
        this.tradeRef = tradeRef;
        this.filedAt = filedAt;
        this.discrepancies = new ArrayList<>(discrepancies);
    }

    public Long getId() {
        return id;
    }

    public String getTradeRef() {
        return tradeRef;
    }

    public Instant getFiledAt() {
        return filedAt;
    }

    public List<BreakFieldDiscrepancyEmbeddable> getDiscrepancies() {
        return discrepancies;
    }
}
