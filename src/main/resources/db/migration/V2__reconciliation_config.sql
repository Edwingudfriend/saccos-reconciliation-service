-- ================================================================
-- V2: Per-SACCO reconciliation configuration
-- ================================================================

CREATE TABLE reconciliation_configs (
    id                                      BIGSERIAL PRIMARY KEY,
    sacco_code                              VARCHAR(50) NOT NULL UNIQUE,
    sacco_name                              VARCHAR(200) NOT NULL,
    active                                  BOOLEAN NOT NULL DEFAULT TRUE,

    -- Matching rules
    amount_tolerance                        NUMERIC(10,2) NOT NULL DEFAULT 1.00,
    strict_reference_match                  BOOLEAN NOT NULL DEFAULT FALSE,

    -- Auto-post rules
    auto_post_enabled                       BOOLEAN NOT NULL DEFAULT FALSE,
    auto_post_max_amount                    NUMERIC(15,2) DEFAULT 50000,
    require_treasurer_approval_above_threshold BOOLEAN NOT NULL DEFAULT TRUE,

    -- Retry rules
    max_retry_count                         INT NOT NULL DEFAULT 3,
    retry_interval_hours                    INT NOT NULL DEFAULT 4,
    escalate_after_hours                    INT NOT NULL DEFAULT 48,

    -- Schedule (per-SACCO — replaces hardcoded cron)
    scheduled_hour                          INT NOT NULL DEFAULT 0,
    scheduled_minute                        INT NOT NULL DEFAULT 30,

    -- Notifications
    notify_treasurer_on_discrepancy         BOOLEAN NOT NULL DEFAULT TRUE,
    notify_on_every_run                     BOOLEAN NOT NULL DEFAULT FALSE,
    alert_threshold_count                   INT NOT NULL DEFAULT 1,
    alert_threshold_amount                  NUMERIC(15,2) DEFAULT 10000,

    -- Metadata
    created_at                              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by                              VARCHAR(200)
);

-- Per-SACCO active providers
CREATE TABLE reconciliation_config_providers (
    config_id   BIGINT NOT NULL REFERENCES reconciliation_configs(id) ON DELETE CASCADE,
    provider    VARCHAR(30) NOT NULL,
    PRIMARY KEY (config_id, provider)
);

-- Seed default config
INSERT INTO reconciliation_configs (
    sacco_code, sacco_name, active,
    amount_tolerance, strict_reference_match,
    auto_post_enabled, auto_post_max_amount, require_treasurer_approval_above_threshold,
    max_retry_count, retry_interval_hours, escalate_after_hours,
    scheduled_hour, scheduled_minute,
    notify_treasurer_on_discrepancy, notify_on_every_run,
    alert_threshold_count, alert_threshold_amount
) VALUES (
    'DEFAULT', 'Default Configuration', TRUE,
    1.00, FALSE,
    FALSE, 50000, TRUE,
    3, 4, 48,
    0, 30,
    TRUE, FALSE,
    1, 10000
);

-- Default providers for DEFAULT config
INSERT INTO reconciliation_config_providers (config_id, provider)
SELECT id, 'MPESA'        FROM reconciliation_configs WHERE sacco_code = 'DEFAULT';
INSERT INTO reconciliation_config_providers (config_id, provider)
SELECT id, 'TIGOPESA'     FROM reconciliation_configs WHERE sacco_code = 'DEFAULT';
INSERT INTO reconciliation_config_providers (config_id, provider)
SELECT id, 'AIRTEL_MONEY' FROM reconciliation_configs WHERE sacco_code = 'DEFAULT';

CREATE INDEX idx_recon_configs_code ON reconciliation_configs(sacco_code);
