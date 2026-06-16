package com.softspark.chaos.flow.chaos;

import java.math.BigDecimal;
import java.util.List;

/**
 * Sealed hierarchy of chaos injection strategies.
 *
 * <p>Each permitted subtype corresponds to one chaos mode available in {@link ChaosOptions}.
 */
public sealed interface ChaosStrategy
    permits ChaosStrategy.Duplicate,
        ChaosStrategy.OutOfOrder,
        ChaosStrategy.Malformed,
        ChaosStrategy.Unbalanced,
        ChaosStrategy.Burst,
        ChaosStrategy.Delay {

  /**
   * Publish {@code count} identical copies of the base event with the same idempotency key.
   *
   * @param count number of duplicate copies
   */
  record Duplicate(int count) implements ChaosStrategy {}

  /**
   * Reorder events in a multi-step flow according to the given index sequence.
   *
   * @param order desired event ordering (zero-based indices into the flow's event sequence)
   */
  record OutOfOrder(List<Integer> order) implements ChaosStrategy {}

  /**
   * Apply field-level mutations to produce an intentionally malformed payload.
   *
   * @param mutations list of mutation directives (e.g., {@code "dropField:amount"})
   */
  record Malformed(List<String> mutations) implements ChaosStrategy {}

  /**
   * Modify {@code net_amount} to create a fee discrepancy.
   *
   * @param delta amount to subtract from {@code net_amount}
   */
  record Unbalanced(BigDecimal delta) implements ChaosStrategy {}

  /**
   * Publish {@code count} events at a controlled rate.
   *
   * @param count total event copies
   * @param ratePerSecond events published per second
   */
  record Burst(int count, int ratePerSecond) implements ChaosStrategy {}

  /**
   * Introduce an artificial publish delay.
   *
   * @param delayMs base delay in milliseconds
   * @param jitterMs random additional jitter in milliseconds
   */
  record Delay(long delayMs, long jitterMs) implements ChaosStrategy {}
}
