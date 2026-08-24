package com.manas.settlementmatch.model;

/**
 * Which leg of a two-sided settlement instruction a message represents.
 * A trade reference is complete only once both sides have arrived.
 */
public enum Side {
    PARTY_A,
    PARTY_B
}
