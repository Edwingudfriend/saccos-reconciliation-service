package com.reimagineafrica.reconciliation.service;

import com.reimagineafrica.reconciliation.client.FineractClient;
import com.reimagineafrica.reconciliation.client.MobileMoneyServiceClient;
import com.reimagineafrica.reconciliation.dto.FineractTransaction;
import com.reimagineafrica.reconciliation.dto.GatewayTransaction;
import com.reimagineafrica.reconciliation.dto.request.ResolveDiscrepancyRequest;
import com.reimagineafrica.reconciliation.dto.request.ReconciliationRunRequest;
import com.reimagineafrica.reconciliation.dto.response.ReconciliationSummary;
import com.reimagineafrica.reconciliation.entity.ReconciliationRecord;
import com.reimagineafrica.reconciliation.entity.ReconciliationRun;
import com.reimagineafrica.reconciliation.entity.UnmatchedPayment;
import com.reimagineafrica.reconciliation.enums.PaymentProvider;
import com.reimagineafrica.reconciliation.enums.ReconciliationStatus;
import com.reimagineafrica.reconciliation.event.ReconciliationEventPublisher;
import com.reimagineafrica.reconciliation.exception.BusinessException;
import com.reimagineafrica.reconciliation.repository.ReconciliationRecordRepository;
import com.reimagineafrica.reconciliation.repository.ReconciliationRunRepository;
import com.reimagineafrica.reconciliation.repository.UnmatchedPaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReconciliationService {

    private final ReconciliationRunRepository runRepository;
    private final ReconciliationRecordRepository recordRepository;
    private final UnmatchedPaymentRepository unmatchedPaymentRepository;
    private final FineractClient fineractClient;
    private final MobileMoneyServiceClient mobileMoneyClient;
    private final ReconciliationEngine engine;
    private final ReconciliationEventPublisher eventPublisher;

    // ── Run Reconciliation ───────────────────────────────────────────

    @Transactional
    public ReconciliationRun runReconciliation(ReconciliationRunRequest request, String triggeredBy) {
        LocalDate date = request.getDate() != null ? request.getDate() : LocalDate.now().minusDays(1);

        // Prevent duplicate runs
        runRepository.findBySaccoCodeAndProviderAndReconciliationDate(
                request.getSaccoCode(), request.getProvider(), date)
                .ifPresent(existing -> {
                    if ("COMPLETED".equals(existing.getRunStatus())) {
                        throw new BusinessException(String.format(
                            "Reconciliation for %s / %s / %s already completed.",
                            request.getSaccoCode(), request.getProvider(), date));
                    }
                });

        ReconciliationRun run = ReconciliationRun.builder()
                .saccoCode(request.getSaccoCode())
                .provider(request.getProvider())
                .reconciliationDate(date)
                .runStatus("RUNNING")
                .startedAt(LocalDateTime.now())
                .triggeredBy(triggeredBy)
                .build();

        runRepository.save(run);
        log.info("Starting reconciliation run: {} / {} / {}", request.getSaccoCode(), request.getProvider(), date);

        try {
            // 1. Fetch gateway transactions
            List<GatewayTransaction> gatewayTxns = mobileMoneyClient
                    .getTransactions(request.getSaccoCode(), request.getProvider(), date);

            // 2. Fetch Fineract transactions
            List<FineractTransaction> fineractTxns = fineractClient.getLoanTransactions(date);

            run.setTotalGatewayTransactions(gatewayTxns.size());
            run.setTotalFineractTransactions(fineractTxns.size());

            // 3. Run matching engine
            List<ReconciliationRecord> records = engine.match(run, gatewayTxns, fineractTxns);

            // 4. Persist records
            recordRepository.saveAll(records);

            // 5. Update run counts
            run.setMatchedCount((int) records.stream().filter(r -> r.getStatus() == ReconciliationStatus.MATCHED).count());
            run.setMissingInFineractCount((int) records.stream().filter(r -> r.getStatus() == ReconciliationStatus.MISSING_IN_FINERACT).count());
            run.setMissingInGatewayCount((int) records.stream().filter(r -> r.getStatus() == ReconciliationStatus.MISSING_IN_GATEWAY).count());
            run.setAmountMismatchCount((int) records.stream().filter(r -> r.getStatus() == ReconciliationStatus.AMOUNT_MISMATCH).count());
            run.setDuplicateCount((int) records.stream().filter(r -> r.getStatus() == ReconciliationStatus.DUPLICATE).count());
            run.setUnresolvedCount(run.getMissingInFineractCount() + run.getAmountMismatchCount() + run.getDuplicateCount());

            run.setRunStatus("COMPLETED");
            run.setCompletedAt(LocalDateTime.now());
            runRepository.save(run);

            log.info("Reconciliation completed: {} matched, {} missing in Fineract, {} mismatches",
                    run.getMatchedCount(), run.getMissingInFineractCount(), run.getAmountMismatchCount());

            // 6. Publish event — notification-service sends summary to treasurer
            if (run.getUnresolvedCount() > 0) {
                eventPublisher.publishDiscrepanciesFound(run);
            }

            return run;

        } catch (Exception e) {
            run.setRunStatus("FAILED");
            run.setErrorMessage(e.getMessage());
            run.setCompletedAt(LocalDateTime.now());
            runRepository.save(run);
            log.error("Reconciliation run failed: {}", e.getMessage(), e);
            throw new BusinessException("Reconciliation failed: " + e.getMessage());
        }
    }

    // ── Run all providers for a SACCO ────────────────────────────────

    @Transactional
    public void runAllProviders(String saccoCode, LocalDate date, String triggeredBy) {
        for (PaymentProvider provider : List.of(
                PaymentProvider.MPESA, PaymentProvider.TIGOPESA, PaymentProvider.AIRTEL_MONEY)) {
            try {
                runReconciliation(ReconciliationRunRequest.builder()
                        .saccoCode(saccoCode)
                        .provider(provider)
                        .date(date)
                        .build(), triggeredBy);
            } catch (Exception e) {
                log.error("Failed to reconcile {} for {}: {}", provider, saccoCode, e.getMessage());
            }
        }
    }

    // ── Resolve discrepancy ──────────────────────────────────────────

    @Transactional
    public void resolveDiscrepancy(UUID recordId, ResolveDiscrepancyRequest request, String resolvedBy) {
        ReconciliationRecord record = recordRepository.findById(recordId)
                .orElseThrow(() -> new BusinessException("Record not found: " + recordId));

        switch (request.getAction()) {
            case "IGNORE" -> {
                record.setStatus(ReconciliationStatus.IGNORED);
                record.setResolutionNote(request.getNotes());
            }
            case "MARK_RESOLVED" -> {
                record.setStatus(ReconciliationStatus.RESOLVED);
                record.setResolutionNote(request.getNotes());
            }
            default -> throw new BusinessException("Unknown resolution action: " + request.getAction());
        }

        record.setResolvedBy(resolvedBy);
        record.setResolvedAt(LocalDateTime.now());
        recordRepository.save(record);

        // Update parent run unresolved count
        ReconciliationRun run = record.getReconciliationRun();
        run.setUnresolvedCount(Math.max(0, run.getUnresolvedCount() - 1));
        runRepository.save(run);
    }

    // ── Resolve unmatched payment ────────────────────────────────────

    @Transactional
    public void resolveUnmatchedPayment(UUID unmatchedId, ResolveDiscrepancyRequest request, String resolvedBy) {
        UnmatchedPayment payment = unmatchedPaymentRepository.findById(unmatchedId)
                .orElseThrow(() -> new BusinessException("Unmatched payment not found: " + unmatchedId));

        if (payment.isResolved()) {
            throw new BusinessException("Payment already resolved.");
        }

        payment.setResolved(true);
        payment.setResolvedBy(resolvedBy);
        payment.setResolvedAt(LocalDateTime.now());
        payment.setResolutionAction(request.getAction());
        unmatchedPaymentRepository.save(payment);

        log.info("Unmatched payment {} resolved by {} — action: {}",
                payment.getGatewayReference(), resolvedBy, request.getAction());
    }

    // ── Queries ──────────────────────────────────────────────────────

    public ReconciliationSummary getSummary(String saccoCode, LocalDate date, PaymentProvider provider) {
        ReconciliationRun run = runRepository
                .findBySaccoCodeAndProviderAndReconciliationDate(saccoCode, provider, date)
                .orElseThrow(() -> new BusinessException("No reconciliation run found."));

        return toSummary(run);
    }

    public Page<ReconciliationRecord> getDiscrepancies(String saccoCode, Pageable pageable) {
        return recordRepository.findUnresolvedBySacco(saccoCode, pageable);
    }

    public Page<UnmatchedPayment> getUnmatchedPayments(String saccoCode, Pageable pageable) {
        return unmatchedPaymentRepository
                .findBySaccoCodeAndResolvedFalseOrderByCreatedAtDesc(saccoCode, pageable);
    }

    private ReconciliationSummary toSummary(ReconciliationRun run) {
        return ReconciliationSummary.builder()
                .saccoCode(run.getSaccoCode())
                .date(run.getReconciliationDate())
                .provider(run.getProvider().name())
                .totalGateway(run.getTotalGatewayTransactions())
                .totalFineract(run.getTotalFineractTransactions())
                .matched(run.getMatchedCount())
                .missingInFineract(run.getMissingInFineractCount())
                .missingInGateway(run.getMissingInGatewayCount())
                .amountMismatch(run.getAmountMismatchCount())
                .duplicates(run.getDuplicateCount())
                .unresolved(run.getUnresolvedCount())
                .unmatchedPaymentsCount(unmatchedPaymentRepository.countBySaccoCodeAndResolvedFalse(run.getSaccoCode()))
                .runStatus(run.getRunStatus())
                .completedAt(run.getCompletedAt() != null ? run.getCompletedAt().toString() : null)
                .build();
    }
}
