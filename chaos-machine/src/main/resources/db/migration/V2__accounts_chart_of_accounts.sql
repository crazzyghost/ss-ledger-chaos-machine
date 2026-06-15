-- Phase 002: Accounts & Chart of Accounts schema

CREATE TABLE IF NOT EXISTS organization (
    organization_id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    type_name TEXT,
    country_name TEXT,
    country_iso_code TEXT,
    status TEXT NOT NULL,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS account_role (
    role TEXT PRIMARY KEY,
    account_code TEXT NOT NULL UNIQUE,
    category TEXT NOT NULL,
    currency TEXT NOT NULL,
    channel TEXT,
    default_va_id TEXT,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS virtual_account (
    va_id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    ownership_type TEXT NOT NULL,
    organization_id TEXT,
    currency TEXT NOT NULL,
    status TEXT NOT NULL,
    channel TEXT,
    account_role TEXT,
    created_via TEXT NOT NULL,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    FOREIGN KEY (organization_id) REFERENCES organization(organization_id)
);

CREATE INDEX IF NOT EXISTS idx_va_organization_id ON virtual_account(organization_id);
CREATE INDEX IF NOT EXISTS idx_va_ownership_type ON virtual_account(ownership_type);
CREATE INDEX IF NOT EXISTS idx_va_status ON virtual_account(status);

CREATE TABLE IF NOT EXISTS flow_slot_config (
    id TEXT PRIMARY KEY,
    flow_type TEXT NOT NULL,
    slot_name TEXT NOT NULL,
    account_role TEXT,
    explicit_va_id TEXT,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    UNIQUE(flow_type, slot_name)
);

CREATE INDEX IF NOT EXISTS idx_flow_slot_config_flow_type ON flow_slot_config(flow_type);
