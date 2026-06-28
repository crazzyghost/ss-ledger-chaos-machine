-- V11: Batch-disbursement run tracking (Phase 016) — reuse batch_run/batch_row behind the new
-- kind='BATCH_DISBURSEMENT' (the kind column is TEXT, so the new value needs no DDL). Add nullable
-- deep-link columns so the run-results UI can fetch the ledger batch summary by batch_id and show the
-- resolved reservation_id. Backward-compatible: existing rows default these columns to NULL.

ALTER TABLE batch_run ADD COLUMN external_batch_id TEXT;
ALTER TABLE batch_run ADD COLUMN reservation_id TEXT;
