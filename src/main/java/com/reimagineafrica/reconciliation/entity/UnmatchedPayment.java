package com.reimagineafrica.reconciliation.entity;

import com.reimagineafrica.reconciliation.enums.PaymentProvider;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Real money received from a member that could NOT be matched
 * to any loan, savings, or contribution account.
 *
 * These MUST be resolved manually — money is sitting unposted.
 */
@Entity
@Table(name = "unmatched_payments")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UnmatchedPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String saccoCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentProvider provider;

    @Column(nullable = false, unique = true)
    private String gatewayReference;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private String senderPhone;

    private String senderName;

    private LocalDateTime paymentTimestamp;

    // Why it couldn't be matched
    private String unmatchedReason;
    /*
     * Possible reasons:
     * - PHONE_NOT_FOUND          No member with this phone number
     * - MULTIPLE_ACTIVE_LOANS    Member has >1 active loan — ambiguous which to repay
     * - NO_ACTIVE_ACCOUNT        Member found but no active loan or savings
     * - AMOUNT_EXCEEDS_BALANCE   Amount > outstanding balance (overpayment)
     * - WRONG_REFERENCE          Payment reference doesn't match any account
     */

    // Manual resolution
    private boolean resolved;
    private String resolvedBy;
    private LocalDateTime resolvedAt;
    private String resolutionAction;    // "POSTED_TO_LOAN", "POSTED_TO_SAVINGS", "REFUNDED"
    private Long fineractTransactionId; // set when manually posted

    // Retry tracking
    @Builder.Default
    private int retryCount = 0;
    private LocalDateTime lastRetryAt;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
