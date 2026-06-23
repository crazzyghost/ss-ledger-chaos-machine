-- Phase 011 (Single Flow Run): guarantee the flow-slot rows the runner needs exist
-- independently of the chaos-bootstrap.yml + ApplicationRunner seeding (which only runs on a
-- clean startup with reprocessed resources). Without a flow_slot_config row, SlotResolver never
-- iterates the slot and silently drops the request's slotOverrides for it, publishing an empty
-- source/destination VA id.
--
-- Organization-account slots (Top-up source, Inter-VA source/destination) carry no account_role:
-- they resolve only from the request's slotOverrides (the operator-picked organization VA).
-- Treasury-Transfer is system-to-system (momo -> momo); the ChartOfAccountsBootstrapRunner keeps
-- these roles in sync on startup, this migration just guarantees the rows exist.
--
-- INSERT OR IGNORE is a no-op where the UNIQUE(flow_type, slot_name) row already exists.

INSERT OR IGNORE INTO flow_slot_config (id, flow_type, slot_name, account_role, explicit_va_id, created_at, updated_at)
VALUES
  ('sfr-topup-source',        'TOPUP_CONFIRMED',             'source',      NULL,                    NULL, '2026-06-23T00:00:00Z', '2026-06-23T00:00:00Z'),
  ('sfr-transfer-source',     'TRANSFER_REQUESTED',          'source',      NULL,                    NULL, '2026-06-23T00:00:00Z', '2026-06-23T00:00:00Z'),
  ('sfr-transfer-dest',       'TRANSFER_REQUESTED',          'destination', NULL,                    NULL, '2026-06-23T00:00:00Z', '2026-06-23T00:00:00Z'),
  ('sfr-treas-transfer-src',  'TREASURY_TRANSFER_COMPLETED', 'source',      'PLATFORM_FLOAT_MTN',     NULL, '2026-06-23T00:00:00Z', '2026-06-23T00:00:00Z'),
  ('sfr-treas-transfer-dest', 'TREASURY_TRANSFER_COMPLETED', 'destination', 'PLATFORM_FLOAT_TELECEL', NULL, '2026-06-23T00:00:00Z', '2026-06-23T00:00:00Z');
