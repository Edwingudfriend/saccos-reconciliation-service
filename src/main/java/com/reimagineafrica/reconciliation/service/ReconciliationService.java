package com.reimagineafrica.reconciliation.service;

import com.reimagineafrica.reconciliation.client.FineractClient;
import com.reimagineafrica.reconciliation.client.MobileMoneyServiceClient;
import com.reimagineafrica.reconciliation.dto.FineractTransaction;
import com.reimagineafrica.reconciliation.dto.GatewayTransaction;
import com.reimagineafrica.reconciliation.dto.request.ResolveDiscrepancyRequest;
import com.reimagineafrica.reconciliation.dto.request.ReconciliationRunRequest;
import com.reimagineafrica.reconciliation.dto.response.ReconciliationSummary;
import com.reimagineafrica.reconciliation.entity.*;
import com.reimagineafrica.reconciliation.enums.ReconciliationStatus;
import com.reimagineafrica.reconciliation.event.ReconciliationEventPublisher;
import com.reimagineafrica.reconciliation.exception.BusinessException;
import com.reimagineafrica.reconciliation.repository.*;
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
    private final ReconciliationConfigService configService;

    @Transactional
    public ReconciliationRun runReconciliation(ReconciliationRunRequest request, String triggeredBy) {
        LocalDate date = request.getDate() != null ? request.getDate() : LocalDate.now().minusDays(1);

        // Resolve per-SACCO config
        ReconciliationConfig config = configService.resolveConfig(request.getSaccoCode());

        // Validate provider is active for this SACCO
        if (!config.getActiveProviders().contains(request.getProvider())) {
            throw new BusinessException(String.format(
                "Provider %s is not configured for SACCO %s. Active providers: %s",
                request.getProvider(), request.getSaccoCode(), config.getActiveProviders()));
        }

        // Prevent duplicate completed runs
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
        log.info("Starting reconciliation: {} / {} / {} (tolerance: {} TZS, strict: {})",
                request.getSaccoCode(), request.getProvider(), date,
                config.getAmountTolerance(), config.getStrictReferenceMatch());

        try {
            List<GatewayTransaction> gatewayTxns = mobileMoneyClient
                    .getTransactions(request.getSaccoCode(), request.getProvider(), date);
            List<FineractTransaction> fineractTxns = fineractClient.getLoanTransactions(date);

            run.setTotalGatewayTransactions(gatewayTxns.size());
            run.setTotalFineractTransactions(fineractTxns.size());

            // Pass config into engine
            List<ReconciliationRecord> records = engine.match(run, gatewayTxns, fineractTxns, config);
            recordRepository.saveAll(records);

            run.setMatchedCount((int) records.stream().filter(r -> r.getStatus() == ReconciliationStatus.MATCHED).count());
            run.setMissingInFineractCount((int) records.stream().filter(r -> r.getStatus() == ReconciliationStatus.MISSING_IN_FINERACT).count());
            run.setMissingInGatewayCount((int) records.stream().filter(r -> r.getStatus() == ReconciliationStatus.MISSING_IN_GATEWAY).count());
            run.setAmountMismatchCount((int) records.stream().filter(r -> r.getStatus() == ReconciliationStatus.AMOUNT_MISMATCH).count());
            run.setDuplicateCount((int) records.stream().filter(r -> r.getStatus() == ReconciliationStatus.DUPLICATE).count());
            run.setUnresolvedCount(run.getMissingInFineractCount() + run.getAmountMismatchCount() + run.getDuplicateCount());

            run.setRunStatus("COMPLETED");
            run.setCompletedAt(LocalDateTime.now());
            runRepository.save(run);

            // Notify based on per-SACCO config thresholds
            boolean shouldNotify = config.getNotifyTreasurerOnDiscrepancy()
                    && run.getUnresolvedCount() >= config.getAlertThresholdCount();

            if (config.getNotifyOnEveryRun() || shouldNotify) {
                eventPublisher.publishDiscrepanciesFound(run);
            }

            log.info("Reconciliation done: {} matched, {} missing in Fineract, {} mismatches",
                    run.getMatchedCount(), run.getMissingInFineractCount(), run.getAmountMismatchCount());
            return run;

        } catch (Exception e) {
            run.setRunStatus("FAILED");
            run.setErrorMessage(e.getMessage());
            run.setCompletedAt(LocalDateTime.now());
            runRepository.save(run);
            log.error("Reconciliation failed: {}", e.getMessage(), e);
            throw new BusinessException("Reconciliation failed: " + e.getMessage());
        }
    }

    @Transactional
    public void runAllProviders(String saccoCode, LocalDate date, String triggeredBy) {
        ReconciliationConfig config = configService.resolveConfig(saccoCode);
        // Only run providers configured for this SACCO
        config.getActiveProviders().forEach(provider -> {
            try {
                runReconciliation(ReconciliationRunRequest.builder()
                        .saccoCode(saccoCode).provider(provider).date(date).build(), triggeredBy);
            } catch (Exception e) {
                log.error("Failed to reconcile {} for {}: {}", provider, saccoCode, e.getMessage());
            }
        });
    }

    @Transactional
    public void resolveDiscrepancy(UUID recordId, ResolveDiscrepancyRequest request, String resolvedBy) {
        ReconciliationRecord record = recordRepository.findById(recordId)
                .orElseThrow(() -> new BusinessException("Record not found: " + recordId));
        switch (request.getAction()) {
            case "IGNORE" -> record.setStatus(ReconciliationStatus.IGNORED);
            case "MARK_RESOLVED" -> record.setStatus(ReconciliationStatus.RESOLVED);
            default -> throw new BusinessException("Unknown action: " + request.getAction());
        }
        record.setResolvedBy(resolvedBy);
        record.setResolvedAt(LocalDateTime.now());
        record.setResolutionNote(request.getNotes());
        recordRepository.save(record);

        ReconciliationRun run = record.getReconciliationRun();
        run.setUnresolvedCount(Math.max(0, run.getUnresolvedCount() - 1));
        runRepository.save(run);
    }

    @Transactional
    public void resolveUnmatchedPayment(UUID unmatchedId, ResolveDiscrepancyRequest request, String resolvedBy) {
        UnmatchedPayment payment = unmatchedPaymentRepository.findById(unmatchedId)
                .orElseThrow(() -> new BusinessException("Unmatched payment not found: " + unmatchedId));
        if (payment.isResolved()) throw new BusinessException("Payment already resolved.");
        payment.setResolved(true);
        payment.setResolvedBy(resolvedBy);
        payment.setResolvedAt(LocalDateTime.now());
        payment.setResolutionAction(request.getAction());
        unmatchedPaymentRepository.save(payment);
    }

    public ReconciliationSummary getSummary(String saccoCode, LocalDate date,
            com.reimagineafrica.reconciliation.enums.PaymentProvider provider) {
        ReconciliationRun run = runRepository
                .findBySaccoCodeAndProviderAndReconciliationDate(saccoCode, provider, date)
                .orElseThrow(() -> new BusinessException("No reconciliation run found."));
        return ReconciliationSummary.builder()
                .saccoCode(run.getSaccoCode()).date(run.getReconciliationDate())
                .provider(run.getProvider().name())
                .totalGateway(run.getTotalGatewayTransactions())
                .totalFineract(run.getTotalFineractTransactions())
                .matched(run.getMatchedCount())
                .missingInFineract(run.getMissingInFineractCount())
                .missingInGateway(run.getMissingInGatewayCount())
                .amountMismatch(run.getAmountMismatchCount())
                .duplicates(run.getDuplicateCount())
                .unresolved(run.getUnresolvedCount())
                .unmatchedPaymentsCount(unmatchedPaymentRepository.countBySaccoCodeAndResolvedFalse(saccoCode))
                .runStatus(run.getRunStatus())
                .completedAt(run.getCompletedAt() != null ? run.getCompletedAt().toString() : null)
                .build();
    }

    public Page<ReconciliationRecord> getDiscrepancies(String saccoCode, Pageable pageable) {
        return recordRepository.findUnresolvedBySacco(saccoCode, pageable);
    }

    public Page<UnmatchedPayment> getUnmatchedPayments(String saccoCode, Pageable pageable) {
        return unmatchedPaymentRepository.findBySaccoCodeAndResolvedFalseOrderByCreatedAtDesc(saccoCode, pageable);
    }
}
