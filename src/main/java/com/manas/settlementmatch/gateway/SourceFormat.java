package com.manas.settlementmatch.gateway;

/**
 * The three wire formats the multi-protocol gateway accepts for the same
 * underlying trade. Each format is produced by a different upstream system
 * (an order-management system's FIX allocation, a confirmation platform's
 * FpML-style XML, a custodian's flat post-trade file) describing what should
 * be the same economic event.
 */
public enum SourceFormat {
    FIX,
    FPML,
    DELIMITED
}
