-- Baseline migration for the chaos machine database
-- This migration establishes the schema version tracking and an initial metadata table

CREATE TABLE IF NOT EXISTS app_metadata (
    key TEXT PRIMARY KEY,
    value TEXT NOT NULL,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

INSERT INTO app_metadata (key, value, created_at, updated_at)
VALUES ('schema_version', '1', datetime('now'), datetime('now'));
