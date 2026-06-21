-- Phase 009: ledger-owned virtual accounts.
-- The VA registry is now a projection of ledger.account.created events. The bootstrap checks
-- "is this account_code already a VA?" before requesting creation from the ledger, so the
-- projection must persist the account_code it materialized for SYSTEM accounts.

ALTER TABLE virtual_account ADD COLUMN account_code TEXT;
CREATE INDEX IF NOT EXISTS idx_virtual_account_code ON virtual_account(account_code);
