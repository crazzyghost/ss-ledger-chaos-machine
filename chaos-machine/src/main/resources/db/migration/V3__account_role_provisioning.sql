-- Phase 025: add provisioning status tracking to account_role

ALTER TABLE account_role ADD COLUMN provisioning_status TEXT NOT NULL DEFAULT 'PENDING';
ALTER TABLE account_role ADD COLUMN provisioned_at TEXT;
ALTER TABLE account_role ADD COLUMN last_error TEXT;

CREATE INDEX IF NOT EXISTS idx_account_role_provisioning_status ON account_role(provisioning_status);
