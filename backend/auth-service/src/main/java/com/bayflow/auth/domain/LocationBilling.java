package com.bayflow.auth.domain;

import java.math.BigDecimal;

/**
 * A location's invoice settings. Tax rates are percentages (8.875 = 8.875 %); a fee is off while
 * null. shopSuppliesPercent is a share of the labour total, capped at shopSuppliesCap when set.
 * paymentTermsDays: 0 = due on receipt.
 */
public record LocationBilling(BigDecimal partsTaxRate, BigDecimal labourTaxRate, BigDecimal feesTaxRate,
        BigDecimal shopSuppliesPercent, BigDecimal shopSuppliesCap, BigDecimal disposalFee, int paymentTermsDays,
        String invoiceFooter) {

    /** No tax, no fees, due on receipt: a new organization's first location. */
    public static final LocationBilling NONE = new LocationBilling(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
            null, null, null, 0, null);
}
