-- Phase 009: surface the ledger account category on the VA projection so the admin UI can render a
-- unified accounts table (Name / Category / Owner / Status / Created) for both the chaos-machine and
-- ledger views. Populated by the ledger.account.created consumer from the event's account_category.

ALTER TABLE virtual_account ADD COLUMN account_category TEXT;
