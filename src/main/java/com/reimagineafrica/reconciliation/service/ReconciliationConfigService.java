package com.reimagineafrica.reconciliation.service;

import com.reimagineafrica.reconciliation.entity.ReconciliationConfig;
import com.reimagineafrica.reconciliation.enums.PaymentProvider;
import com.reimagineafrica.reconciliation.exception.BusinessException;
import com.reimagineafrica.reconciliation.repository.ReconciliationConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReconciliationConfigService {

    private final ReconciliationConfigRepository configRepository;

    // Global defaults from application.yml — used when no per-SACCO config exists
    @Value("${reconciliation.amount-tolerance:1.00}")
    private BigDecimal defaultAmountTolerance;

    @Value("${reconciliation.auto-post-enabled:false}")
    private boolean defaultAutoPostEnabled;

    @Value("${reconciliation.auto-post-max-amount:50000}")
    private BigDecimal defaultAutoPostMaxAmount;

    @Value("${reconciliation.max-retry-count:3}")
    private int defaultMaxRetryCount;

    @Value("${reconciliation.retry-interval-hours:4}")
    private int defaultRetryIntervalHours;

    @Value("${reconciliation.escalate-after-hours:48}")
    private int defaultEscalateAfterHours;

    /**
     * Resolve config for a SACCO.
     * Returns per-SACCO config if it exists and is active.
     * Falls back to defaults from application.yml.
     */
    @Cacheable(value = "reconciliationConfig", key = "#saccoCode")
    public ReconciliationConfig resolveConfig(String saccoCode) {
        if (saccoCode == null || saccoCode.isBlank()) {
            log.warn("No SACCO code provided — using global defaults");
            return buildDefaultConfig();
        }

        return configRepository.findBySaccoCodeAndActiveTrue(saccoCode)
                .orElseGet(() -> {
                    log.warn("No active reconciliation config for '{}' — using defaults", saccoCode);
                    return buildDefaultConfig();
                });
    }

    /**
     * All active SACCOs — used by the scheduler to know who to reconcile.
     */
    public List<ReconciliationConfig> getAllActive() {
        return configRepository.findByActiveTrue();
    }

    // ── CRUD ─────────────────────────────────────────────────────────

    @Transactional
    public ReconciliationConfig create(ReconciliationConfig config) {
        if (configRepository.existsBySaccoCode(config.getSaccoCode())) {
            throw new BusinessException("Config already exists for SACCO: " + config.getSaccoCode());
        }
        validateConfig(config);
        ReconciliationConfig saved = configRepository.save(config);
        log.info("Created reconciliation config for SACCO: {}", config.getSaccoCode());
        return saved;
    }

    @Transactional
    @CacheEvict(value = "reconciliationConfig", key = "#saccoCode")
    public ReconciliationConfig update(String saccoCode, ReconciliationConfig updates, String updatedBy) {
        ReconciliationConfig existing = configRepository.findBySaccoCode(saccoCode)
                .orElseThrow(() -> new BusinessException("Config not found: " + saccoCode));

        validateConfig(updates);

        existing.setActiveProviders(updates.getActiveProviders());
        existing.setAmountTolerance(updates.getAmountTolerance());
        existing.setStrictReferenceMatch(updates.getStrictReferenceMatch());
        existing.setAutoPostEnabled(updates.getAutoPostEnabled());
        existing.setAutoPostMaxAmount(updates.getAutoPostMaxAmount());
        existing.setRequireTreasurerApprovalAboveThreshold(updates.getRequireTreasurerApprovalAboveThreshold());
        existing.setMaxRetryCount(updates.getMaxRetryCount());
        existing.setRetryIntervalHours(updates.getRetryIntervalHours());
        existing.setEscalateAfterHours(updates.getEscalateAfterHours());
        existing.setScheduledHour(updates.getScheduledHour());
        existing.setScheduledMinute(updates.getScheduledMinute());
        existing.setNotifyTreasurerOnDiscrepancy(updates.getNotifyTreasurerOnDiscrepancy());
        existing.setNotifyOnEveryRun(updates.getNotifyOnEveryRun());
        existing.setAlertThresholdCount(updates.getAlertThresholdCount());
        existing.setAlertThresholdAmount(updates.getAlertThresholdAmount());
        existing.setUpdatedBy(updatedBy);

        ReconciliationConfig saved = configRepository.save(existing);
        log.info("Updated reconciliation config for SACCO: {} by {}", saccoCode, updatedBy);
        return saved;
    }

    @Transactional
    @CacheEvict(value = "reconciliationConfig", key = "#saccoCode")
    public void deactivate(String saccoCode, String updatedBy) {
        ReconciliationConfig config = configRepository.findBySaccoCode(saccoCode)
                .orElseThrow(() -> new BusinessException("Config not found: " + saccoCode));
        config.setActive(false);
        config.setUpdatedBy(updatedBy);
        configRepository.save(config);
        log.info("Deactivated reconciliation config for SACCO: {}", saccoCode);
    }

    public ReconciliationConfig getByCode(String saccoCode) {
        return configRepository.findBySaccoCode(saccoCode)
                .orElseThrow(() -> new BusinessException("Config not found: " + saccoCode));
    }

    // ── Validation ───────────────────────────────────────────────────

    private void validateConfig(ReconciliationConfig config) {
        if (config.getActiveProviders() == null || config.getActiveProviders().isEmpty()) {
            throw new BusinessException("At least one payment provider must be configured.");
        }
        if (config.getAmountTolerance().compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("Amount tolerance cannot be negative.");
        }
        if (config.getScheduledHour() < 0 || config.getScheduledHour() > 23) {
            throw new BusinessException("Scheduled hour must be between 0 and 23.");
        }
        if (config.getScheduledMinute() < 0 || config.getScheduledMinute() > 59) {
            throw new BusinessException("Scheduled minute must be between 0 and 59.");
        }
        if (config.getMaxRetryCount() < 0) {
            throw new BusinessException("Max retry count cannot be negative.");
        }
        if (config.getAutoPostEnabled() && config.getAutoPostMaxAmount() == null) {
            throw new BusinessException("Auto-post max amount must be set when auto-post is enabled.");
        }
    }

    // ── Default config ────────────────────────────────────────────────

    private ReconciliationConfig buildDefaultConfig() {
        return ReconciliationConfig.builder()
                .saccoCode("DEFAULT")
                .saccoName("Default Configuration")
                .active(true)
                .activeProviders(Set.of(
                        PaymentProvider.MPESA,
                        PaymentProvider.TIGOPESA,
                        PaymentProvider.AIRTEL_MONEY))
                .amountTolerance(defaultAmountTolerance)
                .strictReferenceMatch(false)
                .autoPostEnabled(defaultAutoPostEnabled)
                .autoPostMaxAmount(defaultAutoPostMaxAmount)
                .requireTreasurerApprovalAboveThreshold(true)
                .maxRetryCount(defaultMaxRetryCount)
                .retryIntervalHours(defaultRetryIntervalHours)
                .escalateAfterHours(defaultEscalateAfterHours)
                .scheduledHour(0)
                .scheduledMinute(30)
                .notifyTreasurerOnDiscrepancy(true)
                .notifyOnEveryRun(false)
                .alertThresholdCount(1)
                .alertThresholdAmount(new BigDecimal("10000"))
                .build();
    }
}
