package com.softspark.chaos.batch.enumeration;

/**
 * Discriminates the origin of a run tracked in {@code batch_run}/{@code batch_row}.
 *
 * <p>{@link #CSV} runs come from an uploaded CSV file (Phase 003); {@link #N_TIMES} runs are
 * synthetic "run a flow N times" runs (Phase 013); {@link #LIFECYCLE} runs are RANDOM-outcome
 * Settlement/Disbursement lifecycles (Phase 014) — each row is a two-publish unit
 * (initiated → completed|failed). All three reuse the same tables behind this discriminator. The
 * column is {@code TEXT}, so adding a kind needs no migration.
 */
public enum RunKind {
  /** A CSV batch upload run. */
  CSV,
  /** An N-Times distinct-transaction run. */
  N_TIMES,
  /** A RANDOM-outcome lifecycle run (each row publishes initiated then completed|failed). */
  LIFECYCLE
}
