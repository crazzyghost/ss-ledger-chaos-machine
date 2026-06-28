-- Phase 017 Task 002 — projection of ledger.transaction.failed events.
-- Correlated to publishes at query time via transaction_request_id (the chaos-supplied id the
-- ledger files under transactionRequestId). Idempotent by envelope event_id.
CREATE TABLE IF NOT EXISTS transaction_failure (
    id                     TEXT PRIMARY KEY,
    event_id               TEXT NOT NULL UNIQUE,
    transaction_request_id TEXT NOT NULL,
    -- The ledger recording id; nullable to match the projection's defensive null-guard (a failure
    -- event without a recording id is still projected rather than dead-lettering at commit).
    ledger_transaction_id  TEXT,
    transaction_type       TEXT NOT NULL,
    failure_code           TEXT,
    failure_reason         TEXT,
    ledger_correlation_id  TEXT,
    idempotency_key        TEXT,
    tenant_id              TEXT,
    occurred_at            TEXT NOT NULL,
    received_at            TEXT NOT NULL,
    payload_json           TEXT
);
CREATE INDEX IF NOT EXISTS idx_tf_request_id    ON transaction_failure (transaction_request_id);
CREATE INDEX IF NOT EXISTS idx_tf_ledger_txn_id ON transaction_failure (ledger_transaction_id);
CREATE INDEX IF NOT EXISTS idx_tf_txn_type      ON transaction_failure (transaction_type);
CREATE INDEX IF NOT EXISTS idx_tf_occurred_at   ON transaction_failure (occurred_at);
