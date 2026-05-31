package com.reimagineafrica.reconciliation.entity;

import com.reimagineafrica.reconciliation.enums.PaymentProvider;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * Per-SACCO reconciliation configuration.
 *
 * Every SACCO can have different rules:
 * - which providers to reconcile
 * - amount tolerance
 * - schedule time
 * - auto-post threshold
 * - retry limits
 */
@Entity
@Table(name = "reconciliation_configs")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ReconciliationConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String saccoCode;

    @Column(nullable = false)
    private String saccoName;

    @Column(nullable = false)
    private boolean active;

    // ── Providers ────────────────────────────────────────────────────
    // Which providers this SACCO actually uses

    @ElementCollection(targetClass = PaymentProvider.class, fetch = FetchType.EAGER)
    @CollectionTable(name = "reconciliation_config_providers",
                     joinColumns = @JoinColumn(name = "config_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "provider")
    @Builder.Default
    private Set<PaymentProvider> activeProviders = Set.of(
            PaymentProvider.MPESA, PaymentProvider.TIGOPESA, PaymentProvider.AIRTEL_MONEY);

    // ── Matching rules ───────────────────────────────────────────────

    @Column(nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal amountTolerance = new BigDecimal("1.00");
    // Max allowed difference before flagging AMOUNT_MISMATCH

    @Column(nullable = false)
    @Builder.Default
    private Boolean strictReferenceMatch = false;
    // true = only match on externalReference, skip fuzzy strategies

    // ── Auto-posting rules ───────────────────────────────────────────

    @Column(nullable = false)
    @Builder.Default
    private Boolean autoPostEnabled = false;
    // If true, automatically post MISSING_IN_FINERACT payments below threshold

    @Column(precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal autoPostMaxAmount = new BigDecimal("50000");
    // Only auto-post if amount <= this value (larger amounts go to manual review)

    @Column(nullable = false)
    @Builder.Default
    private Boolean requireTreasurerApprovalAboveThreshold = true;
    // Payments above autoPostMaxAmount need treasurer sign-off

    // ── Retry rules ──────────────────────────────────────────────────

    @Column(nullable = false)
    @Builder.Default
    private Integer maxRetryCount = 3;
    // How many times to retry matching an unmatched payment

    @Column(nullable = false)
    @Builder.Default
    private Integer retryIntervalHours = 4;
    // How often to retry (hours)

    @Column(nullable = false)
    @Builder.Default
    private Integer escalateAfterHours = 48;
    // Escalate to manager if unresolved after N hours

    // ── Schedule ─────────────────────────────────────────────────────

    @Column(nullable = false)
    @Builder.Default
    private Integer scheduledHour = 0;
    // Hour to run nightly reconciliation (0 = midnight, 6 = 6 AM)

    @Column(nullable = false)
    @Builder.Default
    private Integer scheduledMinute = 30;

    // ── Notifications ────────────────────────────────────────────────

    @Column(nullable = false)
    @Builder.Default
    private Boolean notifyTreasurerOnDiscrepancy = true;

    @Column(nullable = false)
    @Builder.Default
    private Boolean notifyOnEveryRun = false;
    // false = only notify when there are discrepancies

    @Column(nullable = false)
    @Builder.Default
    private Integer alertThresholdCount = 1;
    // Only alert if discrepancy count >= this value

    @Column(precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal alertThresholdAmount = new BigDecimal("10000");
    // Only alert if total discrepancy amount >= this value

    // ── Metadata ─────────────────────────────────────────────────────

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    private String updatedBy;
}
