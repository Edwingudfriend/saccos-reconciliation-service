package com.reimagineafrica.reconciliation.entity;

import com.reimagineafrica.reconciliation.enums.PaymentProvider;
import com.reimagineafrica.reconciliation.enums.ReconciliationStatus;
import com.reimagineafrica.reconciliation.enums.TransactionType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One record = one transaction comparison result.
 *
 * Either gatewayReference or fineractTransactionId may be null
 * (for MISSING_IN_FINERACT and MISSING_IN_GATEWAY cases respectively).
 */
@Entity
@Table(name = "reconciliation_records")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ReconciliationRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reconciliation_run_id", nullable = false)
    private ReconciliationRun reconciliationRun;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReconciliationStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentProvider provider;

    @Enumerated(EnumType.STRING)
    private TransactionType transactionType;

    // Gateway side
    private String gatewayReference;       // M-Pesa transaction ID, Snippe ref etc.
    private BigDecimal gatewayAmount;
    private String gatewayPhone;           // Member phone number
    private LocalDateTime gatewayTimestamp;
    private String gatewayStatus;          // SUCCESS, FAILED etc.

    // Fineract side
    private Long fineractTransactionId;
    private Long fineractLoanId;
    private Long fineractClientId;
    private BigDecimal fineractAmount;
    private LocalDateTime fineractTimestamp;
    private String fineractPaymentType;

    // Discrepancy detail
    private String discrepancyNote;        // Human-readable explanation of the issue
    private BigDecimal amountDifference;   // |gatewayAmount - fineractAmount|

    // Resolution
    private String resolvedBy;
    private LocalDateTime resolvedAt;
    private String resolutionNote;

    // Member info (for display)
    private String memberName;
    private String memberNumber;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
