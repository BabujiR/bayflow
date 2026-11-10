package com.bayflow.workshop.domain;

/** How serious an inspection finding is. */
public enum Severity {
    LOW,
    MEDIUM,
    HIGH,
    /** Unsafe to drive. */
    CRITICAL
}
