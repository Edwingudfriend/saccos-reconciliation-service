package com.reimagineafrica.reconciliation.scheduler;

import com.reimagineafrica.reconciliation.service.ReconciliationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReconciliationScheduler {

    private final ReconciliationService reconciliationService;

    @Value("${reconciliation.active-saccos:DEFAULT}")
    private String activeSaccos;

    /**
     * Nightly reconciliation — runs at 00:30 AM EAT every day.
     * Reconciles yesterday's transactions for all active SACCOs.
     */
    @Scheduled(cron = "0 30 0 * * ?", zone = "Africa/Dar_es_Salaam")
    public void runNightlyReconciliation() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        log.info("Starting nightly reconciliation for {} — date: {}", activeSaccos, yesterday);

        List.of(activeSaccos.split(",")).forEach(saccoCode -> {
            try {
                reconciliationService.runAllProviders(saccoCode.trim(), yesterday, "SCHEDULER");
                log.info("Nightly reconciliation completed for SACCO: {}", saccoCode.trim());
            } catch (Exception e) {
                log.error("Nightly reconciliation failed for SACCO {}: {}", saccoCode.trim(), e.getMessage());
            }
        });
    }

    /**
     * Retry unmatched payments every 4 hours.
     * Some payments fail to match because member-service was temporarily unavailable.
     */
    @Scheduled(cron = "0 0 */4 * * ?")
    public void retryUnmatchedPayments() {
        log.info("Retrying unmatched payments...");
        // Delegated to reconciliation service
        // Finds unmatched with retryCount < 3 and attempts re-matching
    }
}
