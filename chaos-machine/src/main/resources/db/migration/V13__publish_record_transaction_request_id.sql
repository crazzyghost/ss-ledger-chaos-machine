-- Phase 017 Task 003 — capture the transaction request id on publish so a later
-- ledger.transaction.failed can be joined back to its publish by an intrinsic, indexed key.
-- Nullable + forward-looking: historical rows and non-transactional flows keep NULL.
ALTER TABLE publish_record ADD COLUMN transaction_request_id TEXT;
CREATE INDEX IF NOT EXISTS idx_pr_transaction_request_id
    ON publish_record (transaction_request_id);
