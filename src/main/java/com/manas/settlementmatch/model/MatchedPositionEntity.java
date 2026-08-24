package com.manas.settlementmatch.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * A settled position: both sides of a settlement instruction agreed within
 * tolerance. Persisted via Spring Data JPA; H2 in PostgreSQL-compatibility
 * mode for tests and measurement, real PostgreSQL for the production profile.
 */
@Entity
@Table(name = "matched_position", uniqueConstraints = @jakarta.persistence.UniqueConstraint(columnNames = "trade_ref"))
public class MatchedPositionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trade_ref", nullable = false)
    private String tradeRef;

    @Column(nullable = false)
    private String isin;

    @Column(nullable = false)
    private long quantity;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal price;

    @Column(name = "settlement_date", nullable = false)
    private LocalDate settlementDate;

    @Column(nullable = false)
    private String currency;

    @Column(name = "party_a_counterparty", nullable = false)
    private String partyACounterparty;

    @Column(name = "party_b_counterparty", nullable = false)
    private String partyBCounterparty;

    @Column(name = "matched_at", nullable = false)
    private Instant matchedAt;

    protected MatchedPositionEntity() {
        // JPA
    }

    public MatchedPositionEntity(String tradeRef, String isin, long quantity, BigDecimal price,
                                  LocalDate settlementDate, String currency,
                                  String partyACounterparty, String partyBCounterparty, Instant matchedAt) {
        this.tradeRef = tradeRef;
        this.isin = isin;
        this.quantity = quantity;
        this.price = price;
        this.settlementDate = settlementDate;
        this.currency = currency;
        this.partyACounterparty = partyACounterparty;
        this.partyBCounterparty = partyBCounterparty;
        this.matchedAt = matchedAt;
    }

    public Long getId() {
        return id;
    }

    public String getTradeRef() {
        return tradeRef;
    }

    public String getIsin() {
        return isin;
    }

    public long getQuantity() {
        return quantity;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public LocalDate getSettlementDate() {
        return settlementDate;
    }

    public String getCurrency() {
        return currency;
    }

    public String getPartyACounterparty() {
        return partyACounterparty;
    }

    public String getPartyBCounterparty() {
        return partyBCounterparty;
    }

    public Instant getMatchedAt() {
        return matchedAt;
    }
}
