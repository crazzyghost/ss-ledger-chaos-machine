package com.softspark.chaos.flow.chaos;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Caps enforced on all chaos strategy parameters.
 *
 * <p>Bound from {@code chaos.limits.*} configuration properties. Default values ensure safe
 * operation when properties are not explicitly configured.
 *
 * @param maxDuplicates maximum copies allowed by the duplicate strategy
 * @param maxBurst maximum event count allowed by the burst strategy
 * @param maxRatePerSecond maximum publish rate allowed by the burst strategy
 * @param maxDelayMs maximum combined delay (base + jitter) allowed by the delay strategy; also the
 *     ceiling for each N-Times inter-event gap
 * @param maxNTimes maximum iteration count allowed by the N-Times strategy
 * @param maxNTimesSync maximum iteration count allowed for a <em>synchronous</em> N-Times run
 * @param maxSyncDurationMs maximum projected wall-clock (count × effective-max-gap) allowed for a
 *     synchronous N-Times run before it must be moved to ASYNC
 */
@ConfigurationProperties(prefix = "chaos.limits")
public record ChaosLimits(
    @DefaultValue("10") int maxDuplicates,
    @DefaultValue("100") int maxBurst,
    @DefaultValue("1000") int maxRatePerSecond,
    @DefaultValue("30000") long maxDelayMs,
    @DefaultValue("250") int maxNTimes,
    @DefaultValue("25") int maxNTimesSync,
    @DefaultValue("60000") long maxSyncDurationMs) {}
