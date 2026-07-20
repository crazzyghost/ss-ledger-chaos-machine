package com.softspark.chaos.flow;

import com.softspark.chaos.flow.dto.BatchOutcomePolicy;
import java.util.Comparator;
import java.util.stream.IntStream;

/**
 * Resolves a {@link BatchOutcomePolicy} into a deterministic per-item pass/fail pattern for N items.
 *
 * <p>Pure and resume-safe (no {@link Math#random()}/wall-clock): the same policy + seed + N always
 * yields the same pattern, so an interrupted/resumed run reproduces its decisions exactly.
 *
 * <ul>
 *   <li>{@code ALL_PASS}/{@code ALL_FAIL} → every item passes/fails;
 *   <li>{@code COUNT} → the first {@code passCount} items (by sequence) pass, the rest fail;
 *   <li>{@code RANDOM} without a target → {@link OutcomeDecider#succeeds(long, int)} per item;
 *   <li>{@code RANDOM} with a target {@code passCount} → the {@code passCount} highest-ranked items
 *       (by {@link OutcomeDecider#mixed(long, int)}) pass.
 * </ul>
 */
final class BatchOutcomePlan {

  private BatchOutcomePlan() {}

  /**
   * Decides the pass/fail pattern.
   *
   * @param policy the outcome policy
   * @param n the number of items (&gt;= 1)
   * @param decider the deterministic outcome decider
   * @param seed the seed (policy seed when present, else derived from the run id)
   * @return a length-{@code n} array; {@code true} = pass (completed), {@code false} = fail
   */
  static boolean[] decide(BatchOutcomePolicy policy, int n, OutcomeDecider decider, long seed) {
    boolean[] pass = new boolean[n];
    switch (policy.mode()) {
      case ALL_PASS -> java.util.Arrays.fill(pass, true);
      case ALL_FAIL -> java.util.Arrays.fill(pass, false);
      case COUNT -> {
        int k = clamp(policy.passCount(), n);
        for (int i = 0; i < n; i++) {
          pass[i] = i < k;
        }
      }
      case RANDOM -> {
        if (policy.passCount() == null) {
          for (int i = 0; i < n; i++) {
            pass[i] = decider.succeeds(seed, i);
          }
        } else {
          int k = clamp(policy.passCount(), n);
          // Pass the k items with the highest deterministic rank — a resume-safe target selection.
          int[] winners =
              IntStream.range(0, n)
                  .boxed()
                  .sorted(
                      Comparator.comparingLong((Integer i) -> decider.mixed(seed, i)).reversed())
                  .mapToInt(Integer::intValue)
                  .limit(k)
                  .toArray();
          for (int w : winners) {
            pass[w] = true;
          }
        }
      }
    }
    return pass;
  }

  /** Clamps a (possibly null) target into {@code [0, n]}. */
  private static int clamp(Integer passCount, int n) {
    if (passCount == null) {
      return 0;
    }
    return Math.max(0, Math.min(n, passCount));
  }
}
