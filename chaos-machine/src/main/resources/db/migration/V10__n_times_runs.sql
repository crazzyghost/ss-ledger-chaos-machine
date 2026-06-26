-- V10: N-Times run tracking — discriminate batch_run rows by kind and record pacing/mode.
-- Reuses the existing batch_run/batch_row tables (Phase 003) for N-Times runs (Phase 013).
-- Backward-compatible: existing CSV rows default to kind='CSV'; filename is already nullable.

ALTER TABLE batch_run ADD COLUMN kind TEXT NOT NULL DEFAULT 'CSV';
ALTER TABLE batch_run ADD COLUMN pacing TEXT;
ALTER TABLE batch_run ADD COLUMN mode TEXT;
