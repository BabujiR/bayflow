package com.bayflow.workshop.invoicing;

/** How a payment was taken (at the counter; online payments come later). */
public enum PaymentMethod {
    CASH, CARD, CHECK, BANK_TRANSFER, OTHER
}
