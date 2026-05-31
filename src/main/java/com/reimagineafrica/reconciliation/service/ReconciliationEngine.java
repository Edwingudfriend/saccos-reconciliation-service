package com.reimagineafrica.reconciliation.service;

import com.reimagineafrica.reconciliation.dto.FineractTransaction;
import com.reimagineafrica.reconciliation.dto.GatewayTransaction;
import com.reimagineafrica.reconciliation.entity.ReconciliationRecord;
import com.reimagineafrica.reconciliation.entity.ReconciliationRun;
import com.reimagineafrica.reconciliation.entity.UnmatchedPayment;
import com.reimagineafrica.reconciliation.enums.ReconciliationStatus;
import com.reimagineafrica.reconciliation.repository.UnmatchedPaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Core matching engine.
 *
 * Matching strategy (cascade — tries each in order):
 * 1. externalReference → Fineract receiptNumber
 * 2. gatewayReference  → Fineract receiptNumber
 * 3. gatewayReference  → Fineract paymentReceiptNumber
 * 4. amount + phone    → Fineract amount + clientId (fuzzy — flagged for review)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReconciliationEngine {

    private final UnmatchedPaymentRepository unmatchedPaymentRepository;
    private final MemberLookupService memberLookupService;

    @Value("${reconciliation.amount-tolerance:1.00}")
    private BigDecimal amountTolerance;

    /**
     * Compare gateway transactions against Fineract transactions.
     * Returns a list of ReconciliationRecord to persist.
     */
    public List<ReconciliationRecord> match(ReconciliationRun run,
                                             List<GatewayTransaction> gatewayTxns,
                                             List<FineractTransaction> fineractTxns) {

        List<ReconciliationRecord> records = new ArrayList<>();
        Set<Long> matchedFineractIds = new HashSet<>();

        // Index Fineract transactions by all possible reference fields
        Map<String, FineractTransaction> byReceiptNumber = indexBy(fineractTxns, FineractTransaction::getReceiptNumber);
        Map<String, FineractTransaction> byPaymentReceipt = indexBy(fineractTxns, FineractTransaction::getPaymentReceiptNumber);

        for (GatewayTransaction gtx : gatewayTxns) {
            // Skip failed gateway transactions
            if (!"SUCCESS".equalsIgnoreCase(gtx.getStatus())) continue;

            FineractTransaction match = null;

            // Strategy 1: externalReference → receiptNumber
            if (gtx.getExternalReference() != null) {
                match = byReceiptNumber.get(gtx.getExternalReference());
            }

            // Strategy 2: gatewayReference → receiptNumber
            if (match == null && gtx.getGatewayReference() != null) {
                match = byReceiptNumber.get(gtx.getGatewayReference());
            }

            // Strategy 3: gatewayReference → paymentReceiptNumber
            if (match == null && gtx.getGatewayReference() != null) {
                match = byPaymentReceipt.get(gtx.getGatewayReference());
            }

            if (match == null) {
                // MISSING IN FINERACT — money received but not posted
                records.add(buildRecord(run, ReconciliationStatus.MISSING_IN_FINERACT, gtx, null,
                        "Payment received from " + gtx.getSenderPhone() +
                        " but no matching Fineract transaction found."));
                handleUnmatchedPayment(run.getSaccoCode(), gtx);
                continue;
            }

            // Check for duplicate (already matched)
            if (matchedFineractIds.contains(match.getFineractTransactionId())) {
                records.add(buildRecord(run, ReconciliationStatus.DUPLICATE, gtx, match,
                        "Fineract transaction " + match.getFineractTransactionId() +
                        " matched to multiple gateway transactions."));
                continue;
            }

            matchedFineractIds.add(match.getFineractTransactionId());

            // Check amount match
            BigDecimal diff = gtx.getGatewayAmount()
                    .subtract(match.getAmount()).abs();

            if (diff.compareTo(amountTolerance) <= 0) {
                records.add(buildRecord(run, ReconciliationStatus.MATCHED, gtx, match, null));
            } else {
                records.add(buildRecord(run, ReconciliationStatus.AMOUNT_MISMATCH, gtx, match,
                        String.format("Gateway: %.2f TZS | Fineract: %.2f TZS | Diff: %.2f TZS",
                                gtx.getGatewayAmount(), match.getAmount(), diff)));
            }
        }

        // Find orphan Fineract transactions (mobile money type, no gateway record)
        for (FineractTransaction ft : fineractTxns) {
            if (!matchedFineractIds.contains(ft.getFineractTransactionId())
                    && isMobileMoneyPayment(ft)) {
                records.add(buildRecord(run, ReconciliationStatus.MISSING_IN_GATEWAY, null, ft,
                        "Fineract transaction " + ft.getFineractTransactionId() +
                        " (" + ft.getPaymentTypeName() + ") has no gateway record."));
            }
        }

        return records;
    }

    private void handleUnmatchedPayment(String saccoCode, GatewayTransaction gtx) {
        // Avoid saving duplicate unmatched payments
        if (unmatchedPaymentRepository.existsByGatewayReference(gtx.getGatewayReference())) {
            return;
        }

        String reason = memberLookupService.findUnmatchedReason(gtx.getSenderPhone(), gtx.getGatewayAmount());

        UnmatchedPayment unmatched = UnmatchedPayment.builder()
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
                .build();

        unmatchedPaymentRepository.save(unmatched);
        log.warn("Unmatched payment: {} {} TZS from {} — {}",
                gtx.getGatewayReference(), gtx.getGatewayAmount(), gtx.getSenderPhone(), reason);
    }

    private ReconciliationRecord buildRecord(ReconciliationRun run,
                                              ReconciliationStatus status,
                                              GatewayTransaction gtx,
                                              FineractTransaction ft,
                                              String note) {
        ReconciliationRecord.ReconciliationRecordBuilder b = ReconciliationRecord.builder()
                .reconciliationRun(run)
                .status(status)
                .provider(run.getProvider())
                .discrepancyNote(note);

        if (gtx != null) {
            b.gatewayReference(gtx.getGatewayReference())
             .gatewayAmount(gtx.getGatewayAmount())
             .gatewayPhone(gtx.getSenderPhone())
             .gatewayTimestamp(gtx.getTimestamp())
             .gatewayStatus(gtx.getStatus());
        }

        if (ft != null) {
            b.fineractTransactionId(ft.getFineractTransactionId())
             .fineractLoanId(ft.getLoanId())
             .fineractClientId(ft.getClientId())
             .fineractAmount(ft.getAmount())
             .fineractTimestamp(ft.getTransactionDate())
             .fineractPaymentType(ft.getPaymentTypeName());

            if (gtx != null && ft.getAmount() != null) {
                b.amountDifference(gtx.getGatewayAmount().subtract(ft.getAmount()).abs());
            }
        }

        return b.build();
    }

    private boolean isMobileMoneyPayment(FineractTransaction ft) {
        if (ft.getPaymentTypeName() == null) return false;
        String type = ft.getPaymentTypeName().toUpperCase();
        return type.contains("MPESA") || type.contains("TIGO")
                || type.contains("AIRTEL") || type.contains("HALO")
                || type.contains("SELCOM") || type.contains("MOBILE");
    }

    private Map<String, FineractTransaction> indexBy(List<FineractTransaction> txns,
                                                       java.util.function.Function<FineractTransaction, String> keyFn) {
        return txns.stream()
                .filter(t -> keyFn.apply(t) != null)
                .collect(Collectors.toMap(keyFn, t -> t, (a, b) -> a));
    }
}
