package com.reimagineafrica.reconciliation.entity;

import com.reimagineafrica.reconciliation.enums.PaymentProvider;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * One reconciliation run = one provider + one date + one SACCO tenant.
 * Contains all matched/mismatched records from that run.
 */
@Entity
@Table(name = "reconciliation_runs",
       uniqueConstraints = @UniqueConstraint(columnNames = {"sacco_code", "provider", "reconciliation_date"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ReconciliationRun {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String saccoCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentProvider provider;

    @Column(nullable = false)
    private LocalDate reconciliationDate;

    // Counts
    @Builder.Default
    private int totalGatewayTransactions = 0;

    @Builder.Default
    private int totalFineractTransactions = 0;

    @Builder.Default
    private int matchedCount = 0;

    @Builder.Default
    private int missingInFineractCount = 0;

    @Builder.Default
    private int missingInGatewayCount = 0;

    @Builder.Default
    private int amountMismatchCount = 0;

    @Builder.Default
    private int duplicateCount = 0;

    @Builder.Default
    private int unresolvedCount = 0;

    private String runStatus;     // RUNNING, COMPLETED, FAILED
    private String errorMessage;

    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private String triggeredBy;   // "SCHEDULER" or user ID

    @OneToMany(mappedBy = "reconciliationRun", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ReconciliationRecord> records = new ArrayList<>();

    @CreationTimestamp
    private LocalDateTime createdAt;
}
