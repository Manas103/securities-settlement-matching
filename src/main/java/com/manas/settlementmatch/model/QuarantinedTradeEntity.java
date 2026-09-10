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
 * A trade reference whose FIX, FpML-style, and delimited descriptions all
 * arrived but disagreed on at least one canonical field, so ingestion was
 * quarantined rather than guessing which format's value is correct. This
 * is a distinct record from {@link BreakEntity}: a break is two
 * counterparties disagreeing on an already-normalized instruction beyond a
 * configured tolerance; a quarantined trade is one trade's own three
 * self-descriptions disagreeing before normalization ever completes.
 */
@Entity
@Table(name = "quarantined_trade")
public class QuarantinedTradeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trade_ref", nullable = false)
    private String tradeRef;

    @Column(name = "quarantined_at", nullable = false)
    private Instant quarantinedAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "quarantine_field_disagreement", joinColumns = @JoinColumn(name = "quarantined_trade_id"))
    private List<QuarantinedFieldDisagreementEmbeddable> disagreements = new ArrayList<>();

    protected QuarantinedTradeEntity() {
        // JPA
    }

    public QuarantinedTradeEntity(String tradeRef, Instant quarantinedAt, List<QuarantinedFieldDisagreementEmbeddable> disagreements) {
        this.tradeRef = tradeRef;
        this.quarantinedAt = quarantinedAt;
        this.disagreements = new ArrayList<>(disagreements);
    }

    public Long getId() {
        return id;
    }

    public String getTradeRef() {
        return tradeRef;
    }

    public Instant getQuarantinedAt() {
        return quarantinedAt;
    }

    public List<QuarantinedFieldDisagreementEmbeddable> getDisagreements() {
        return disagreements;
    }
}
