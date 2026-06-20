package com.softspark.chaos.organization.seed;

/**
 * Policy governing when the {@link ReferenceDataSeeder} seeds reference data at startup.
 */
public enum SeedPolicy {
  /** Seed only when the {@code country} table is empty (default). */
  IF_EMPTY,
  /** Seed on every startup (upsert-if-absent semantics keep it idempotent). */
  ALWAYS,
  /** Never seed automatically; the manual refresh endpoint still works. */
  NEVER
}
