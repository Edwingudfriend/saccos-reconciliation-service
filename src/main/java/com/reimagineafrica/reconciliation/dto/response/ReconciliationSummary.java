package com.reimagineafrica.reconciliation.dto.response;

import lombok.*;

import java.time.LocalDate;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ReconciliationSummary {
    private String saccoCode;
    private LocalDate date;
    private String provider;

    private int totalGateway;
    private int totalFineract;
    private int matched;
    private int missingInFineract;
    private int missingInGateway;
    private int amountMismatch;
    private int duplicates;
    private int unresolved;

    private long unmatchedPaymentsCount;     // Payments with no member match at all
    private String runStatus;
    private String completedAt;

    public double getMatchRate() {
        if (totalGateway == 0) return 0;
        return (double) matched / totalGateway * 100;
    }
}
