-- Phase 010: Currencies & Supported Countries schema (shared with Phase 009)

-- Task 001: managed currency reference table (UUID id + ISO-4217 code).
CREATE TABLE IF NOT EXISTS currency (
    currency_id TEXT PRIMARY KEY,
    code TEXT NOT NULL UNIQUE,
    name TEXT NOT NULL,
    symbol TEXT,
    status TEXT NOT NULL,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

-- Task 002: country gains a nullable primary-currency FK.
ALTER TABLE country ADD COLUMN primary_currency_id TEXT REFERENCES currency(currency_id);
CREATE INDEX IF NOT EXISTS idx_country_primary_currency ON country(primary_currency_id);

-- Task 003: curated subset of countries available on the onboarding form.
CREATE TABLE IF NOT EXISTS supported_country (
    supported_country_id TEXT PRIMARY KEY,
    country_id TEXT NOT NULL UNIQUE REFERENCES country(country_id),
    status TEXT NOT NULL,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

-- Task 004: organization snapshots the onboarded country's primary currency.
-- SQLite supports only one column per ALTER TABLE ADD COLUMN.
ALTER TABLE organization ADD COLUMN primary_currency_id TEXT;
ALTER TABLE organization ADD COLUMN primary_currency_code TEXT;
