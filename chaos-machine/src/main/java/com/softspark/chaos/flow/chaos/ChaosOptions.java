package com.softspark.chaos.flow.chaos;

import io.soabase.recordbuilder.core.RecordBuilder;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.lang.Nullable;

/**
 * Chaos injection configuration attached to a {@link com.softspark.chaos.flow.FlowRequest}.
 *
 * <p>Strategies are mutually exclusive: the first non-null strategy is applied. All strategy
 * parameters are validated against {@link ChaosLimits} before execution.
 *
 * @param duplicate produces {@code n} identical copies of the event
 * @param outOfOrder reorders events in a multi-step flow
 * @param malformed applies field mutations to produce an intentionally invalid payload
 * @param unbalanced shifts the {@code net_amount} to create an unbalanced fee scenario
 * @param burst publishes {@code count} events at a controlled rate
 * @param delay introduces an artificial publish delay
 */
@RecordBuilder
public record ChaosOptions(
    @Nullable DuplicateOptions duplicate,
    @Nullable OutOfOrderOptions outOfOrder,
    @Nullable MalformedOptions malformed,
    @Nullable UnbalancedOptions unbalanced,
    @Nullable BurstOptions burst,
    @Nullable DelayOptions delay) {

  /**
   * Options for the duplicate strategy.
   *
   * @param count number of duplicate copies (capped by {@code chaos.limits.max-duplicates})
   */
  public record DuplicateOptions(int count) {}

  /**
   * Options for the out-of-order strategy.
   *
   * @param order zero-based indices specifying the desired event sequence in a multi-step flow
   */
  public record OutOfOrderOptions(List<Integer> order) {}

  /**
   * Options for the malformed strategy.
   *
   * @param mutations list of mutation directives (e.g., {@code "dropField:amount"},
   *     {@code "negativeAmount"})
   */
  public record MalformedOptions(List<String> mutations) {}

  /**
   * Options for the unbalanced strategy.
   *
   * @param delta amount subtracted from {@code net_amount}, creating a fee discrepancy
   */
  public record UnbalancedOptions(BigDecimal delta) {}

  /**
   * Options for the burst strategy.
   *
   * @param count total events to publish (capped by {@code chaos.limits.max-burst})
   * @param ratePerSecond maximum publish rate (capped by {@code chaos.limits.max-rate-per-second})
   */
  public record BurstOptions(int count, int ratePerSecond) {}

  /**
   * Options for the delay strategy.
   *
   * @param delayMs base delay in milliseconds
   * @param jitterMs random additional jitter; effective delay = {@code delayMs + random(0,
   *     jitterMs)}
   */
  public record DelayOptions(long delayMs, long jitterMs) {}
}
