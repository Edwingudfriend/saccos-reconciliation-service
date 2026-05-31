package com.reimagineafrica.reconciliation.client;

import com.reimagineafrica.reconciliation.dto.FineractTransaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class FineractClient {

    private final WebClient fineractWebClient;

    /**
     * Fetch all loan repayment transactions for a given date.
     * Iterates active loans and pulls transactions within the date window.
     */
    public List<FineractTransaction> getLoanTransactions(LocalDate date) {
        List<FineractTransaction> result = new ArrayList<>();

        try {
            // Get all active loans
            Map response = fineractWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/loans")
                            .queryParam("loanStatus", "300") // active
                            .queryParam("limit", "500")
                            .build())
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response == null) return result;

            List<Map<String, Object>> loans = (List<Map<String, Object>>) response.get("pageItems");
            if (loans == null) return result;

            for (Map<String, Object> loan : loans) {
                Long loanId = ((Number) loan.get("id")).longValue();
                Long clientId = ((Number) loan.get("clientId")).longValue();
                result.addAll(getLoanTransactionsForDate(loanId, clientId, date));
            }
        } catch (Exception e) {
            log.error("Failed to fetch Fineract loan transactions for {}: {}", date, e.getMessage());
        }

        return result;
    }

    private List<FineractTransaction> getLoanTransactionsForDate(Long loanId, Long clientId, LocalDate date) {
        List<FineractTransaction> result = new ArrayList<>();
        try {
            Map response = fineractWebClient.get()
                    .uri("/loans/" + loanId + "/transactions")
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response == null) return result;
            List<Map<String, Object>> txns = (List<Map<String, Object>>) response.get("transactions");
            if (txns == null) return result;

            for (Map<String, Object> txn : txns) {
                // Filter by date
                Map<String, Object> dateMap = (Map<String, Object>) txn.get("date");
                if (dateMap == null) continue;

                LocalDate txnDate = LocalDate.of(
                        ((Number) dateMap.get("year")).intValue(),
                        ((Number) dateMap.get("monthValue")).intValue(),
                        ((Number) dateMap.get("dayOfMonth")).intValue()
                );

                if (!txnDate.equals(date)) continue;

                Map<String, Object> paymentDetail = (Map<String, Object>) txn.get("paymentDetailData");
                String receiptNumber = null;
                String paymentTypeName = null;

                if (paymentDetail != null) {
                    receiptNumber = (String) paymentDetail.get("receiptNumber");
                    Map<String, Object> paymentType = (Map<String, Object>) paymentDetail.get("paymentType");
                    if (paymentType != null) {
                        paymentTypeName = (String) paymentType.get("name");
                    }
                }

                result.add(FineractTransaction.builder()
                        .fineractTransactionId(((Number) txn.get("id")).longValue())
                        .loanId(loanId)
                        .clientId(clientId)
                        .receiptNumber(receiptNumber)
                        .amount(new BigDecimal(txn.get("amount").toString()))
                        .transactionType((String) ((Map<?, ?>) txn.get("type")).get("value"))
                        .paymentTypeName(paymentTypeName)
                        .transactionDate(txnDate.atStartOfDay())
                        .isReversed(Boolean.TRUE.equals(txn.get("manuallyReversed")))
                        .build());
            }
        } catch (Exception e) {
            log.warn("Could not fetch transactions for loan {}: {}", loanId, e.getMessage());
        }
        return result;
    }

    /**
     * Post a loan repayment to Fineract.
     * Called when resolving a MISSING_IN_FINERACT discrepancy.
     */
    public Long postLoanRepayment(Long loanId, BigDecimal amount, LocalDate date, String receiptNumber) {
        Map<String, Object> payload = Map.of(
                "transactionDate", date.toString(),
                "transactionAmount", amount,
                "paymentTypeId", 1,
                "receiptNumber", receiptNumber,
                "dateFormat", "yyyy-MM-dd",
                "locale", "en"
        );

        Map response = fineractWebClient.post()
                .uri("/loans/" + loanId + "/transactions?command=repayment")
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        return ((Number) response.get("resourceId")).longValue();
    }

    /**
     * Post a savings deposit to Fineract.
     */
    public Long postSavingsDeposit(Long savingsId, BigDecimal amount, LocalDate date, String receiptNumber) {
        Map<String, Object> payload = Map.of(
                "transactionDate", date.toString(),
                "transactionAmount", amount,
                "paymentTypeId", 1,
                "receiptNumber", receiptNumber,
                "dateFormat", "yyyy-MM-dd",
                "locale", "en"
        );

        Map response = fineractWebClient.post()
                .uri("/savingsaccounts/" + savingsId + "/transactions?command=deposit")
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        return ((Number) response.get("resourceId")).longValue();
    }
}
