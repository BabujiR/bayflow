package com.bayflow.workshop.config;

import java.math.BigDecimal;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Settings under {@code bayflow.workshop}. */
@ConfigurationProperties("bayflow.workshop")
public class WorkshopProperties {

    /** Hourly labour rate used for estimates and as the default for labour entries. */
    private BigDecimal labourRate = new BigDecimal("95.00");

    /** ISO 4217 currency code shown with amounts. */
    private String currency = "USD";

    public BigDecimal getLabourRate() { return labourRate; }
    public void setLabourRate(BigDecimal labourRate) { this.labourRate = labourRate; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
}
