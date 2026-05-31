-- ================================================================
-- V1: SACCOS Reconciliation Service — Initial Schema
-- ================================================================

CREATE TABLE reconciliation_runs (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sacco_code                  VARCHAR(50) NOT NULL,
    provider                    VARCHAR(30) NOT NULL,
    reconciliation_date         DATE NOT NULL,
    total_gateway_transactions  INT NOT NULL DEFAULT 0,
    total_fineract_transactions INT NOT NULL DEFAULT 0,
    matched_count               INT NOT NULL DEFAULT 0,
    missing_in_fineract_count   INT NOT NULL DEFAULT 0,
    missing_in_gateway_count    INT NOT NULL DEFAULT 0,
    amount_mismatch_count       INT NOT NULL DEFAULT 0,
    duplicate_count             INT NOT NULL DEFAULT 0,
    unresolved_count            INT NOT NULL DEFAULT 0,
    run_status                  VARCHAR(20) NOT NULL DEFAULT 'RUNNING',
    error_message               TEXT,
    started_at                  TIMESTAMP,
    completed_at                TIMESTAMP,
    triggered_by                VARCHAR(200),
    created_at                  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (sacco_code, provider, reconciliation_date)
);

CREATE TABLE reconciliation_records (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reconciliation_run_id       UUID NOT NULL REFERENCES reconciliation_runs(id) ON DELETE CASCADE,
    status                      VARCHAR(30) NOT NULL,
    provider                    VARCHAR(30) NOT NULL,
    transaction_type            VARCHAR(30),
    gateway_reference           VARCHAR(200),
    gateway_amount              NUMERIC(15,2),
    gateway_phone               VARCHAR(20),
    gateway_timestamp           TIMESTAMP,
    gateway_status              VARCHAR(20),
    fineract_transaction_id     BIGINT,
    fineract_loan_id            BIGINT,
    fineract_client_id          BIGINT,
    fineract_amount             NUMERIC(15,2),
    fineract_timestamp          TIMESTAMP,
    fineract_payment_type       VARCHAR(100),
    discrepancy_note            TEXT,
    amount_difference           NUMERIC(15,2),
    resolved_by                 VARCHAR(200),
    resolved_at                 TIMESTAMP,
    resolution_note             TEXT,
    member_name                 VARCHAR(200),
    member_number               VARCHAR(50),
    created_at                  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE unmatched_payments (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sacco_code                  VARCHAR(50) NOT NULL,
    provider                    VARCHAR(30) NOT NULL,
    gateway_reference           VARCHAR(200) NOT NULL UNIQUE,
    amount                      NUMERIC(15,2) NOT NULL,
    sender_phone                VARCHAR(20) NOT NULL,
    sender_name                 VARCHAR(200),
    payment_timestamp           TIMESTAMP,
    unmatched_reason            VARCHAR(50),
    resolved                    BOOLEAN NOT NULL DEFAULT FALSE,
    resolved_by                 VARCHAR(200),
    resolved_at                 TIMESTAMP,
    resolution_action           VARCHAR(50),
    fineract_transaction_id     BIGINT,
    retry_count                 INT NOT NULL DEFAULT 0,
    last_retry_at               TIMESTAMP,
    created_at                  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_recon_runs_sacco ON reconciliation_runs(sacco_code, reconciliation_date);
CREATE INDEX idx_recon_records_run ON reconciliation_records(reconciliation_run_id);
CREATE INDEX idx_recon_records_status ON reconciliation_records(status);
CREATE INDEX idx_unmatched_sacco ON unmatched_payments(sacco_code, resolved);
CREATE INDEX idx_unmatched_phone ON unmatched_payments(sender_phone);
