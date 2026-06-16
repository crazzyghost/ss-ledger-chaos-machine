-- V4: Publish history, batch run, and batch row tables for Phase 003 — Transaction Flow Engine

CREATE TABLE IF NOT EXISTS publish_record (
    id TEXT PRIMARY KEY,
    event_id TEXT NOT NULL,
    event_type TEXT NOT NULL,
    topic TEXT NOT NULL,
    source TEXT NOT NULL,
    correlation_id TEXT,
    idempotency_key TEXT,
    tenant_id TEXT,
    source_va_id TEXT,
    destination_va_id TEXT,
    status TEXT NOT NULL,
    intentional_failure INTEGER NOT NULL DEFAULT 0,
    chaos_strategy TEXT,
    payload_json TEXT,
    batch_id TEXT,
    batch_row_id TEXT,
    kafka_offset INTEGER,
    kafka_partition INTEGER,
    created_at TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_pr_event_type ON publish_record(event_type);
CREATE INDEX IF NOT EXISTS idx_pr_source_va ON publish_record(source_va_id);
CREATE INDEX IF NOT EXISTS idx_pr_destination_va ON publish_record(destination_va_id);
CREATE INDEX IF NOT EXISTS idx_pr_correlation_id ON publish_record(correlation_id);
CREATE INDEX IF NOT EXISTS idx_pr_batch_id ON publish_record(batch_id);
CREATE INDEX IF NOT EXISTS idx_pr_created_at ON publish_record(created_at);

CREATE TABLE IF NOT EXISTS batch_run (
    id TEXT PRIMARY KEY,
    flow_type TEXT NOT NULL,
    filename TEXT,
    total INTEGER NOT NULL DEFAULT 0,
    succeeded INTEGER NOT NULL DEFAULT 0,
    failed INTEGER NOT NULL DEFAULT 0,
    invalid INTEGER NOT NULL DEFAULT 0,
    status TEXT NOT NULL,
    created_at TEXT NOT NULL,
    completed_at TEXT
);

CREATE TABLE IF NOT EXISTS batch_row (
    id TEXT PRIMARY KEY,
    batch_id TEXT NOT NULL,
    row_number INTEGER NOT NULL,
    status TEXT NOT NULL,
    event_id TEXT,
    error TEXT,
    created_at TEXT NOT NULL,
    FOREIGN KEY (batch_id) REFERENCES batch_run(id)
);

CREATE INDEX IF NOT EXISTS idx_batch_row_batch_id ON batch_row(batch_id);
