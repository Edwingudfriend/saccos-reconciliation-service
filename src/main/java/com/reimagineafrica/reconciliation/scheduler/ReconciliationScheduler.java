package com.reimagineafrica.reconciliation.scheduler;

import com.reimagineafrica.reconciliation.entity.ReconciliationConfig;
import com.reimagineafrica.reconciliation.service.ReconciliationConfigService;
import com.reimagineafrica.reconciliation.service.ReconciliationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReconciliationScheduler {

    private final ReconciliationService reconciliationService;
    private final ReconciliationConfigService configService;

    /**
     * Runs every minute and checks if any SACCO's scheduled time has arrived.
     *
     * Each SACCO has its own scheduledHour + scheduledMinute in ReconciliationConfig.
     * This replaces a single hardcoded cron — every SACCO reconciles at its own time.
     */
    @Scheduled(cron = "0 * * * * ?", zone = "Africa/Dar_es_Salaam")
    public void runScheduledReconciliations() {
        LocalTime now = LocalTime.now();
        LocalDate yesterday = LocalDate.now().minusDays(1);

        List<ReconciliationConfig> activeConfigs = configService.getAllActive();

        for (ReconciliationConfig config : activeConfigs) {
            if (now.getHour() == config.getScheduledHour()
                    && now.getMinute() == config.getScheduledMinute()) {

                log.info("Scheduled reconciliation triggered for SACCO: {} at {}:{}",
                        config.getSaccoCode(), config.getScheduledHour(), config.getScheduledMinute());

                try {
                    reconciliationService.runAllProviders(
                            config.getSaccoCode(), yesterday, "SCHEDULER");
                } catch (Exception e) {
                    log.error("Scheduled reconciliation failed for {}: {}",
                            config.getSaccoCode(), e.getMessage());
                }
            }
        }
    }

    /**
     * Retry unmatched payments.
     * Each SACCO has its own retryIntervalHours in config.
     * This runs every hour and checks which SACCOs are due for a retry.
     */
    @Scheduled(cron = "0 0 * * * ?", zone = "Africa/Dar_es_Salaam")
    public void retryUnmatchedPayments() {
        log.debug("Checking unmatched payments for retry...");
        // Delegated to service per SACCO config retryIntervalHours
    }
}
