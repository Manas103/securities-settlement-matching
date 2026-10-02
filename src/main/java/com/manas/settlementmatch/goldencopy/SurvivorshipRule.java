package com.manas.settlementmatch.goldencopy;

import java.util.List;

/**
 * The declared survivorship rule for one golden field: an ordered
 * precedence of vendors to try in turn (first non-null value wins), or an
 * empty precedence meaning no rule is declared at all (any cross-vendor
 * disagreement is held, never guessed). {@code dataOwner} names who the
 * field is held for when the rule, declared or absent, cannot resolve it.
 */
public record SurvivorshipRule(GoldenField field, List<Vendor> precedence, String dataOwner) {
}
