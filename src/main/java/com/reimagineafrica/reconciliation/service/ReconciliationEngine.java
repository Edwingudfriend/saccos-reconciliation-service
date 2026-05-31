package com.reimagineafrica.reconciliation.service;

import com.reimagineafrica.reconciliation.dto.FineractTransaction;
import com.reimagineafrica.reconciliation.dto.GatewayTransaction;
import com.reimagineafrica.reconciliation.entity.ReconciliationConfig;
import com.reimagineafrica.reconciliation.entity.ReconciliationRecord;
import com.reimagineafrica.reconciliation.entity.ReconciliationRun;
import com.reimagineafrica.reconciliation.entity.UnmatchedPayment;
import com.reimagineafrica.reconciliation.enums.ReconciliationStatus;
import com.reimagineafrica.reconciliation.repository.UnmatchedPaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReconciliationEngine {

    private final UnmatchedPaymentRepository unmatchedPaymentRepository;
    private final MemberLookupService memberLookupService;

    /**
     * Compare gateway transactions against Fineract transactions.
     * Uses per-SACCO config for tolerance and matching strategy.
     */
    public List<ReconciliationRecord> match(ReconciliationRun run,
                                             List<GatewayTransaction> gatewayTxns,
                                             List<FineractTransaction> fineractTxns,
                                             ReconciliationConfig config) {

        List<ReconciliationRecord> records = new ArrayList<>();
        Set<Long> matchedFineractIds = new HashSet<>();

        // Index Fineract transactions by reference fields
        Map<String, FineractTransaction> byReceiptNumber = indexBy(fineractTxns,
                FineractTransaction::getReceiptNumber);
        Map<String, FineractTransaction> byPaymentReceipt = indexBy(fineractTxns,
                FineractTransaction::getPaymentReceiptNumber);

        for (GatewayTransaction gtx : gatewayTxns) {
            if (!"SUCCESS".equalsIgnoreCase(gtx.getStatus())) continue;

            FineractTransaction match = null;

            // Strategy 1: externalReference → receiptNumber (always tried)
            if (gtx.getExternalReference() != null) {
                match = byReceiptNumber.get(gtx.getExternalReference());
            }

            // Strategy 2 & 3: only if strictReferenceMatch is false
            if (match == null && !config.getStrictReferenceMatch()) {
                if (gtx.getGatewayReference() != null) {
                    match = byReceiptNumber.get(gtx.getGatewayReference());
                }
                if (match == null && gtx.getGatewayReference() != null) {
                    match = byPaymentReceipt.get(gtx.getGatewayReference());
                }
            }

            if (match == null) {
                records.add(buildRecord(run, ReconciliationStatus.MISSING_IN_FINERACT, gtx, null,
                        "Payment from " + gtx.getSenderPhone() + " has no Fineract match."));
                handleUnmatchedPayment(run.getSaccoCode(), gtx);
                continue;
            }

            if (matchedFineractIds.contains(match.getFineractTransactionId())) {
                records.add(buildRecord(run, ReconciliationStatus.DUPLICATE, gtx, match,
                        "Fineract transaction " + match.getFineractTransactionId() +
                        " matched to multiple gateway transactions."));
                continue;
            }

            matchedFineractIds.add(match.getFineractTransactionId());

            // Use per-SACCO amount tolerance
            BigDecimal diff = gtx.getGatewayAmount().subtract(match.getAmount()).abs();

            if (diff.compareTo(config.getAmountTolerance()) <= 0) {
                records.add(buildRecord(run, ReconciliationStatus.MATCHED, gtx, match, null));
            } else {
                records.add(buildRecord(run, ReconciliationStatus.AMOUNT_MISMATCH, gtx, match,
                        String.format("Gateway: %.2f | Fineract: %.2f | Diff: %.2f (tolerance: %.2f TZS)",
                                gtx.getGatewayAmount(), match.getAmount(), diff,
                                config.getAmountTolerance())));
            }
        }

        // Orphan Fineract transactions
        for (FineractTransaction ft : fineractTxns) {
            if (!matchedFineractIds.contains(ft.getFineractTransactionId())
                    && isMobileMoneyPayment(ft)) {
                records.add(buildRecord(run, ReconciliationStatus.MISSING_IN_GATEWAY, null, ft,
                        "Fineract transaction " + ft.getFineractTransactionId() + " has no gateway record."));
            }
        }

        return records;
    }

    private void handleUnmatchedPayment(String saccoCode, GatewayTransaction gtx) {
        if (unmatchedPaymentRepository.existsByGatewayReference(gtx.getGatewayReference())) return;

        String reason = memberLookupService.findUnmatchedReason(gtx.getSenderPhone(), gtx.getGatewayAmount());

        unmatchedPaymentRepository.save(UnmatchedPayment.builder()
                .saccoCode(saccoCode)
                .provider(gtx.getProvider())
                .gatewayReference(gtx.getGatewayReference())
                .amount(gtx.getGatewayAmount())
                .senderPhone(gtx.getSenderPhone())
                .senderName(gtx.getSenderName())
                .paymentTimestamp(gtx.getTimestamp())
                .unmatchedReason(reason)
                .resolved(false)
                .retryCount(0)
                .build());

        log.warn("Unmatched payment: {} {} TZS from {} — {}",
                gtx.getGatewayReference(), gtx.getGatewayAmount(), gtx.getSenderPhone(), reason);
    }

    private ReconciliationRecord buildRecord(ReconciliationRun run, ReconciliationStatus status,
                                              GatewayTransaction gtx, FineractTransaction ft, String note) {
        var b = ReconciliationRecord.builder()
                .reconciliationRun(run).status(status)
                .provider(run.getProvider()).discrepancyNote(note);
        if (gtx != null) b.gatewayReference(gtx.getGatewayReference())
                .gatewayAmount(gtx.getGatewayAmount()).gatewayPhone(gtx.getSenderPhone())
                .gatewayTimestamp(gtx.getTimestamp()).gatewayStatus(gtx.getStatus());
        if (ft != null) b.fineractTransactionId(ft.getFineractTransactionId())
                .fineractLoanId(ft.getLoanId()).fineractClientId(ft.getClientId())
                .fineractAmount(ft.getAmount()).fineractTimestamp(ft.getTransactionDate())
                .fineractPaymentType(ft.getPaymentTypeName());
        if (gtx != null && ft != null && ft.getAmount() != null)
            b.amountDifference(gtx.getGatewayAmount().subtract(ft.getAmount()).abs());
        return b.build();
    }

    private boolean isMobileMoneyPayment(FineractTransaction ft) {
        if (ft.getPaymentTypeName() == null) return false;
        String t = ft.getPaymentTypeName().toUpperCase();
        return t.contains("MPESA") || t.contains("TIGO") || t.contains("AIRTEL")
                || t.contains("HALO") || t.contains("SELCOM") || t.contains("MOBILE");
    }

    private Map<String, FineractTransaction> indexBy(List<FineractTransaction> txns,
            java.util.function.Function<FineractTransaction, String> keyFn) {
        return txns.stream().filter(t -> keyFn.apply(t) != null)
                .collect(Collectors.toMap(keyFn, t -> t, (a, b) -> a));
    }
}
