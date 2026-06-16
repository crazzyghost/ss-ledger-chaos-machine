package com.softspark.chaos.flow.chaos;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.softspark.chaos.base.Ids;
import com.softspark.chaos.exception.BadRequestException;
import com.softspark.chaos.flow.FlowContext;
import com.softspark.chaos.flow.model.FlowType;
import com.softspark.chaos.kafka.EventEnvelope;
import com.softspark.chaos.kafka.EventMetadata;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/**
 * Expands a base {@link EventEnvelope} into a list of {@link PreparedSend} units according to the
 * active {@link ChaosOptions} strategy.
 *
 * <p>When {@code options} is {@code null} a single normal {@link PreparedSend} is returned.
 * Strategy caps are validated against {@link ChaosLimits} before expansion.
 */
@Component
public class ChaosPlan {

  private static final Random RANDOM = new Random();

  private final ChaosLimits limits;
  private final ObjectMapper objectMapper;

  public ChaosPlan(ChaosLimits limits, ObjectMapper objectMapper) {
    this.limits = limits;
    this.objectMapper = objectMapper;
  }

  /**
   * Expands the base envelope into one or more {@link PreparedSend} units.
   *
   * @param base the canonical event envelope
   * @param ctx the resolved flow context
   * @param options the chaos configuration; {@code null} for normal (non-chaos) execution
   * @return an ordered list of prepared sends; never empty
   */
  public List<PreparedSend> expand(
      EventEnvelope<?> base, FlowContext ctx, @Nullable ChaosOptions options) {
    if (options == null) {
      return List.of(new PreparedSend(base, null, Duration.ZERO, null));
    }

    if (options.duplicate() != null) {
      return applyDuplicate(base, options.duplicate());
    }
    if (options.outOfOrder() != null) {
      return applyOutOfOrder(base, options.outOfOrder(), ctx.request().flowType());
    }
    if (options.malformed() != null) {
      return applyMalformed(base, options.malformed());
    }
    if (options.unbalanced() != null) {
      return applyUnbalanced(base, options.unbalanced(), ctx.request().flowType());
    }
    if (options.burst() != null) {
      return applyBurst(base, options.burst(), ctx);
    }
    if (options.delay() != null) {
      return applyDelay(base, options.delay());
    }

    return List.of(new PreparedSend(base, null, Duration.ZERO, null));
  }

  // ── Strategy implementations ──────────────────────────────────────────────

  private List<PreparedSend> applyDuplicate(
      EventEnvelope<?> base, ChaosOptions.DuplicateOptions opts) {
    int count = opts.count();
    if (count > limits.maxDuplicates()) {
      throw new BadRequestException(
          "Duplicate count " + count + " exceeds limit of " + limits.maxDuplicates());
    }
    String label = "DUPLICATE:" + count;
    List<PreparedSend> result = new ArrayList<>(count);
    for (int i = 0; i < count; i++) {
      result.add(new PreparedSend(base, null, Duration.ZERO, label));
    }
    return result;
  }

  private List<PreparedSend> applyOutOfOrder(
      EventEnvelope<?> base, ChaosOptions.OutOfOrderOptions opts, FlowType flowType) {
    boolean isMultiStep =
        flowType == FlowType.SETTLEMENT_INITIATED
            || flowType == FlowType.SETTLEMENT_COMPLETED
            || flowType == FlowType.SETTLEMENT_FAILED
            || flowType == FlowType.TREASURY_PREFUND_COMPLETED
            || flowType == FlowType.TREASURY_SWEEP_COMPLETED;

    if (!isMultiStep) {
      return List.of(new PreparedSend(base, null, Duration.ZERO, "OUT_OF_ORDER:unsupported"));
    }
    return List.of(new PreparedSend(base, null, Duration.ZERO, "OUT_OF_ORDER:noop"));
  }

  private List<PreparedSend> applyMalformed(
      EventEnvelope<?> base, ChaosOptions.MalformedOptions opts) {
    String rawOverride = MalformedMutators.apply(base, opts.mutations(), objectMapper);
    String label = "MALFORMED:" + String.join(",", opts.mutations());
    return List.of(new PreparedSend(base, rawOverride, Duration.ZERO, label));
  }

  private List<PreparedSend> applyUnbalanced(
      EventEnvelope<?> base, ChaosOptions.UnbalancedOptions opts, FlowType flowType) {
    boolean isSupported =
        flowType == FlowType.COLLECTION_COMPLETED || flowType == FlowType.DISBURSEMENT_COMPLETED;
    if (!isSupported) {
      return List.of(new PreparedSend(base, null, Duration.ZERO, "UNBALANCED:unsupported"));
    }
    String label = "UNBALANCED:delta=" + opts.delta();
    return List.of(new PreparedSend(base, null, Duration.ZERO, label));
  }

  private List<PreparedSend> applyBurst(
      EventEnvelope<?> base, ChaosOptions.BurstOptions opts, FlowContext ctx) {
    int count = opts.count();
    int rate = opts.ratePerSecond();

    if (count > limits.maxBurst()) {
      throw new BadRequestException(
          "Burst count " + count + " exceeds limit of " + limits.maxBurst());
    }
    if (rate > limits.maxRatePerSecond()) {
      throw new BadRequestException(
          "Burst rate " + rate + " exceeds limit of " + limits.maxRatePerSecond());
    }

    long delayBetweenMs = rate > 0 ? 1000L / rate : 0L;
    String label = "BURST:" + count;
    List<PreparedSend> result = new ArrayList<>(count);

    for (int i = 0; i < count; i++) {
      String newEventId = Ids.generate();
      EventEnvelope<?> copy = rebuildWithNewEventId(base, newEventId, ctx);
      Duration delay = Duration.ofMillis(delayBetweenMs * i);
      result.add(new PreparedSend(copy, null, delay, label));
    }
    return result;
  }

  private List<PreparedSend> applyDelay(EventEnvelope<?> base, ChaosOptions.DelayOptions opts) {
    long totalDelay = opts.delayMs() + opts.jitterMs();
    if (totalDelay > limits.maxDelayMs()) {
      throw new BadRequestException(
          "Delay " + totalDelay + "ms exceeds limit of " + limits.maxDelayMs() + "ms");
    }
    long jitter = opts.jitterMs() > 0 ? (long) (RANDOM.nextDouble() * opts.jitterMs()) : 0L;
    Duration delay = Duration.ofMillis(opts.delayMs() + jitter);
    String label = "DELAY:" + opts.delayMs() + "+" + opts.jitterMs();
    return List.of(new PreparedSend(base, null, delay, label));
  }

  @SuppressWarnings("unchecked")
  private <T> EventEnvelope<T> rebuildWithNewEventId(
      EventEnvelope<?> base, String newEventId, FlowContext ctx) {
    EventMetadata newMeta =
        new EventMetadata(
            base.metadata().correlationId(),
            base.metadata().idempotencyKey(),
            base.metadata().tenantId());
    return new EventEnvelope<>(
        newEventId,
        base.eventType(),
        Instant.now(),
        base.source(),
        base.version(),
        (T) base.data(),
        newMeta);
  }
}
