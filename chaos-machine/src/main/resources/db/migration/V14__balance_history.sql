-- Phase 018 Task 001 — per-account, append-only projection of ledger.balance.updated events.
-- Idempotent by envelope event_id; balances stored as exact decimal strings; ordered for display by
-- (occurred_at DESC, last_entry_sequence DESC). Currency is best-effort from the VA registry.
CREATE TABLE IF NOT EXISTS balance_history (
    id                    TEXT PRIMARY KEY,
    event_id              TEXT NOT NULL UNIQUE,
    account_id            TEXT NOT NULL,
    available_balance     TEXT NOT NULL,
    pending_balance       TEXT NOT NULL,
    reserved_balance      TEXT NOT NULL,
    total_balance         TEXT NOT NULL,
    total_debits          TEXT,
    total_credits         TEXT,
    last_entry_sequence   INTEGER NOT NULL,
    balance_as_of         TEXT NOT NULL,
    currency              TEXT,
    idempotency_key       TEXT,
    ledger_correlation_id TEXT,
    tenant_id             TEXT,
    occurred_at           TEXT NOT NULL,
    received_at           TEXT NOT NULL,
    payload_json          TEXT
);
CREATE INDEX IF NOT EXISTS idx_bh_account      ON balance_history (account_id);
CREATE INDEX IF NOT EXISTS idx_bh_account_seq  ON balance_history (account_id, last_entry_sequence);
CREATE INDEX IF NOT EXISTS idx_bh_account_time ON balance_history (account_id, occurred_at);
