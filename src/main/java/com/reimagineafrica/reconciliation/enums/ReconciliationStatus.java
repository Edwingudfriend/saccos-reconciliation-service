package com.reimagineafrica.reconciliation.enums;

/**
 * Result of matching a payment gateway transaction against Fineract.
 */
public enum ReconciliationStatus {
    MATCHED,                // Perfect match — amount, reference, date all align
    AMOUNT_MISMATCH,        // Reference matched but amounts differ
    MISSING_IN_FINERACT,    // Gateway shows payment but Fineract has no record
    MISSING_IN_GATEWAY,     // Fineract has transaction but gateway has no record
    DUPLICATE,              // Same payment posted to Fineract more than once
    STATUS_MISMATCH,        // Gateway says SUCCESS but Fineract shows PENDING/FAILED
    RESOLVED,               // Operator manually resolved the discrepancy
    IGNORED                 // Operator marked as not requiring action
}
