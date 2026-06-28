-- Phase 020 Task 001 — one domain-tagged projection table for the ledger's inbound dead letters.
-- Dedup by (dlt_topic, dlt_partition, dlt_offset) — always present even when the original payload is
-- unparseable. The `source` discriminator lets chaos-own consumer DLTs be folded in later without a
-- migration.
CREATE TABLE IF NOT EXISTS dlq (
    id                     TEXT PRIMARY KEY,
    dlt_topic              TEXT NOT NULL,
    dlt_partition          INTEGER NOT NULL,
    dlt_offset             INTEGER NOT NULL,
    dead_letter_id         TEXT,
    original_topic         TEXT NOT NULL,
    domain                 TEXT NOT NULL,
    source                 TEXT NOT NULL,
    event_type             TEXT,
    event_id               TEXT,
    transaction_id         TEXT,
    transaction_type       TEXT,
    failure_classification TEXT,
    error_type             TEXT,
    error_message          TEXT,
    retry_count            INTEGER,
    original_partition     INTEGER,
    original_offset        INTEGER,
    original_key           TEXT,
    dead_lettered_at       TEXT,
    original_payload_json  TEXT,
    raw_dlt_json           TEXT,
    received_at            TEXT NOT NULL,
    CONSTRAINT uq_dlq_coords UNIQUE (dlt_topic, dlt_partition, dlt_offset)
);
CREATE INDEX IF NOT EXISTS idx_dlq_domain   ON dlq (domain);
CREATE INDEX IF NOT EXISTS idx_dlq_txn      ON dlq (transaction_id);
CREATE INDEX IF NOT EXISTS idx_dlq_txn_type ON dlq (transaction_type);
CREATE INDEX IF NOT EXISTS idx_dlq_orig     ON dlq (original_topic);
CREATE INDEX IF NOT EXISTS idx_dlq_received ON dlq (received_at);
