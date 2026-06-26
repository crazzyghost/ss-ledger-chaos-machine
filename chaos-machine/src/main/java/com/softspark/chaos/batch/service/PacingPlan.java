package com.softspark.chaos.batch.service;

import com.softspark.chaos.flow.chaos.NTimesOptions;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.LongSupplier;

/**
 * Encapsulates the concurrency and inter-row delay for an N-Times run, mapping a {@link Pacing}
 * onto the {@link BatchRunner}'s execution shape:
 *
 * <ul>
 *   <li>{@code BURST} → wide concurrency, no delay (a genuine contention spike);
 *   <li>{@code LINEAR} → single-threaded, fixed gap between rows;
 *   <li>{@code RANDOM} → single-threaded, a fresh gap in {@code [minDelayMs, maxDelayMs]}.
 * </ul>
 *
 * @param concurrency desired parallelism (further bounded by {@code chaos.batch.workers})
 * @param delayMs supplies the delay (ms) applied before each row after the first
 */
public record PacingPlan(int concurrency, LongSupplier delayMs) {

  /** Wide-concurrency, zero-delay plan for {@code BURST} pacing. */
  public static PacingPlan burst() {
    return new PacingPlan(Integer.MAX_VALUE, () -> 0L);
  }

  /** Single-threaded, fixed-gap plan for {@code LINEAR} pacing. */
  public static PacingPlan linear(long fixedDelayMs) {
    return new PacingPlan(1, () -> fixedDelayMs);
  }

  /** Single-threaded, random-gap plan for {@code RANDOM} pacing. */
  public static PacingPlan random(long minDelayMs, long maxDelayMs) {
    return new PacingPlan(
        1,
        () ->
            minDelayMs >= maxDelayMs
                ? minDelayMs
                : ThreadLocalRandom.current().nextLong(minDelayMs, maxDelayMs + 1));
  }

  /**
   * Builds the pacing plan for the given validated N-Times options.
   *
   * @param options the validated N-Times options
   * @return the matching pacing plan
   */
  public static PacingPlan forOptions(NTimesOptions options) {
    return switch (options.pacing()) {
      case BURST -> burst();
      case LINEAR -> linear(options.fixedDelayMs());
      case RANDOM -> random(options.minDelayMs(), options.maxDelayMs());
    };
  }
}
