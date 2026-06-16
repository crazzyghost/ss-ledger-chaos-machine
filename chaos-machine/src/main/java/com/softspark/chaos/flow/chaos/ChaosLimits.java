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
 * @param maxDelayMs maximum combined delay (base + jitter) allowed by the delay strategy
 */
@ConfigurationProperties(prefix = "chaos.limits")
public record ChaosLimits(
    @DefaultValue("10") int maxDuplicates,
    @DefaultValue("100") int maxBurst,
    @DefaultValue("1000") int maxRatePerSecond,
    @DefaultValue("30000") long maxDelayMs) {}
