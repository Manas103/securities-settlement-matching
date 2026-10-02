package com.manas.settlementmatch.goldencopy;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One vendor's own description of one security, keyed on that vendor's own
 * native identifier (not the internal crosswalk key). Any golden field may
 * be {@code null}: a vendor simply not carrying a field is a normal,
 * expected condition the survivorship rules must handle, not an error.
 */
public record VendorFeedRecord(
        Vendor vendor,
        String vendorRecordId,
        String nativeIdentifier,
        String name,
        String country,
        String currency,
        String sector,
        LocalDate maturity,
        BigDecimal coupon,
        String exchange
) {
}
