package com.softspark.chaos.flow;

import org.springframework.stereotype.Component;

/**
 * Decides the SUCCEED/FAIL outcome of a RANDOM lifecycle deterministically from a per-run seed and
 * the lifecycle's iteration index.
 *
 * <p>Determinism (no {@link Math#random()} / wall-clock) keeps the RANDOM runner resume-safe and
 * unit-testable: the same {@code (seed, index)} always yields the same outcome, so a resumed run
 * reproduces its decisions exactly. The mix is a SplitMix64-style finalizer over
 * {@code seed}/{@code index} that yields a well-distributed ~50/50 split.
 */
@Component
public class OutcomeDecider {

  /**
   * Returns whether the lifecycle at {@code index} under {@code seed} should SUCCEED.
   *
   * @param seed the per-run seed (see {@link #seedFor(String)})
   * @param index the zero-based lifecycle index within the run
   * @return {@code true} for SUCCEED (publish completed), {@code false} for FAIL (publish failed)
   */
  public boolean succeeds(long seed, int index) {
    long z = seed + (index + 1L) * 0x9E3779B97F4A7C15L;
    z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
    z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
    z = z ^ (z >>> 31);
    return (z & 1L) == 0L;
  }

  /**
   * Derives a stable seed from a run id, so a given run's outcomes are reproducible on resume.
   *
   * @param runId the run id
   * @return a deterministic seed for the run
   */
  public long seedFor(String runId) {
    if (runId == null) {
      return 0L;
    }
    long h = 1125899906842597L; // a large prime
    for (int i = 0; i < runId.length(); i++) {
      h = 31 * h + runId.charAt(i);
    }
    return h;
  }
}
