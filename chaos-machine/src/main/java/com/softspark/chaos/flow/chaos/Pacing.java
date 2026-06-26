package com.softspark.chaos.flow.chaos;

/**
 * Inter-event timing for an {@link NTimesOptions} run.
 *
 * <p>Distinct from the top-level {@link ChaosStrategy.Burst} strategy: here {@code BURST} is a
 * <em>pacing</em> (no inter-event delay), not a duplicate-keyed volume probe.
 */
public enum Pacing {
  /** No delay between consecutive iterations. */
  BURST,
  /** A fixed delay ({@code fixedDelayMs}) between consecutive iterations. */
  LINEAR,
  /** A fresh random delay in {@code [minDelayMs, maxDelayMs]} between consecutive iterations. */
  RANDOM
}
