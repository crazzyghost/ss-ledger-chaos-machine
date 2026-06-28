-- Phase 019 Task 001 — stateful projection of ledger.reservation.created/.released into one row
-- per reservation_id, upserted as events arrive. Status advances monotonically (never regresses;
-- terminal is sticky). Batch reservations accumulate multiple releases (release_event_count).
CREATE TABLE IF NOT EXISTS reservation (
    reservation_id        TEXT PRIMARY KEY,
    account_id            TEXT NOT NULL,
    transaction_id        TEXT NOT NULL,
    reservation_type      TEXT NOT NULL,
    disbursement_batch_id TEXT,
    amount                TEXT NOT NULL,
    currency              TEXT,
    status                TEXT NOT NULL,
    created_event_id      TEXT,
    last_event_id         TEXT NOT NULL,
    release_event_count   INTEGER NOT NULL DEFAULT 0,
    tenant_id             TEXT,
    created_at            TEXT,
    updated_at            TEXT NOT NULL,
    terminal_at           TEXT,
    payload_json          TEXT
);
CREATE INDEX IF NOT EXISTS idx_resv_txn    ON reservation (transaction_id);
CREATE INDEX IF NOT EXISTS idx_resv_acct   ON reservation (account_id);
CREATE INDEX IF NOT EXISTS idx_resv_batch  ON reservation (disbursement_batch_id);
CREATE INDEX IF NOT EXISTS idx_resv_status ON reservation (status);
