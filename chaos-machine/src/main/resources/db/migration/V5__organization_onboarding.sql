-- Phase 008: Organization Onboarding schema

CREATE TABLE IF NOT EXISTS country (
    country_id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    iso_code TEXT NOT NULL UNIQUE,
    status TEXT NOT NULL,
    modified_date TEXT NOT NULL,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS organization_type (
    organization_type_id TEXT PRIMARY KEY,
    name TEXT NOT NULL UNIQUE,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

-- Extend the organization table with onboarding foreign keys, contact fields, and snapshots.
-- SQLite supports only one column per ALTER TABLE ADD COLUMN.
-- type_name, country_name, country_iso_code already exist from V2.
ALTER TABLE organization ADD COLUMN organization_type_id TEXT REFERENCES organization_type(organization_type_id);
ALTER TABLE organization ADD COLUMN country_id TEXT REFERENCES country(country_id);
ALTER TABLE organization ADD COLUMN primary_contact_email TEXT;
ALTER TABLE organization ADD COLUMN phone_numbers TEXT;
ALTER TABLE organization ADD COLUMN country_status TEXT;
ALTER TABLE organization ADD COLUMN country_modified_date TEXT;

CREATE INDEX IF NOT EXISTS idx_organization_country_id ON organization(country_id);
CREATE INDEX IF NOT EXISTS idx_organization_type_id ON organization(organization_type_id);

-- Transactional outbox for organization.onboarded events.
CREATE TABLE IF NOT EXISTS outbox_event (
    outbox_id TEXT PRIMARY KEY,
    aggregate_type TEXT NOT NULL,
    aggregate_id TEXT NOT NULL,
    event_id TEXT NOT NULL,
    event_type TEXT NOT NULL,
    partition_key TEXT,
    payload_json TEXT NOT NULL,
    status TEXT NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    last_error TEXT,
    published_at TEXT,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_outbox_status_created ON outbox_event(status, created_at);
