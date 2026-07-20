-- Phase 023: Consistency Checks - Reconciliation Mismatch Projection Table
-- Creates the table that stores reconciliation mismatch events consumed from Kafka
-- for toast notification purposes. The table is append-only and serves as a projection
-- of the ledger.reconciliation.mismatch event stream.

CREATE TABLE reconciliation_mismatch (
    id TEXT PRIMARY KEY,
    check_id TEXT NOT NULL UNIQUE,
    type TEXT NOT NULL,
    initiator_type TEXT NOT NULL,
    as_of TEXT NOT NULL,
    initiated_at TEXT NOT NULL,
    completed_at TEXT NOT NULL,
    discrepancy_count INTEGER NOT NULL,
    consumed_at TEXT NOT NULL
);

-- Index to support polling queries (GET /reconciliation-mismatches?since=)
CREATE INDEX idx_reconciliation_mismatch_consumed_at ON reconciliation_mismatch(consumed_at);
