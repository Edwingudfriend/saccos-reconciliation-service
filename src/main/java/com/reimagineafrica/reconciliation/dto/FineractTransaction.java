package com.reimagineafrica.reconciliation.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Normalised transaction from Fineract.
 * Pulled from /loans/{id}/transactions and /savingsaccounts/{id}/transactions.
 */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class FineractTransaction {
    private Long fineractTransactionId;
    private Long loanId;
    private Long clientId;
    private String receiptNumber;           // The reference stored at posting
    private String paymentReceiptNumber;    // Alternative reference field
    private BigDecimal amount;
    private String transactionType;
    private String paymentTypeName;
    private LocalDateTime transactionDate;
    private boolean isReversed;
}
