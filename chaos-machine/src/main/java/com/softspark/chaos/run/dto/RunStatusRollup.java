package com.softspark.chaos.run.dto;

/**
 * Coarse status rollup for a run in the run-grouped feed ({@code GET /api/v0/runs}).
 *
 * <p>For a <em>tracked</em> run it is derived from the {@code batch_run} status
 * ({@code RUNNING}/{@code COMPLETED}/{@code COMPLETED_WITH_FAILURES}/{@code FAILED}); for an
 * <em>untracked</em> correlation group it is derived from the publish-status tally of its
 * {@code publish_record} rows (any {@code FAILED} publish → {@link #HAS_FAILURES}, else
 * {@link #ALL_PUBLISHED}). This is the Kafka publish-status rollup only; ledger-side acceptance is
 * resolved separately by the frontend per expanded event page (ADR-031).
 */
public enum RunStatusRollup {
  /** A tracked run still in progress (events may be incomplete). */
  RUNNING,
  /** Every event in the run published to Kafka successfully. */
  ALL_PUBLISHED,
  /** At least one event failed to publish (but at least one succeeded). */
  HAS_FAILURES,
  /** The run terminated with no successful publishes. */
  FAILED
}
