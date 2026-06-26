package com.softspark.chaos.flow.chaos;

import io.soabase.recordbuilder.core.RecordBuilder;
import org.springframework.lang.Nullable;

/**
 * Options for the <strong>N-Times</strong> chaos strategy: run a single flow {@code count} times
 * against the <em>same</em> source/destination accounts, producing {@code count}
 * genuinely-distinct transactions (fresh event id + fresh derived idempotency key + fresh payload
 * {@code *_request_id} per iteration).
 *
 * <p>Contrast with {@link ChaosStrategy.Burst}: Burst rebuilds duplicate-keyed copies (one logical
 * effect after dedup); N-Times re-rolls the business transaction id so every iteration is a real,
 * independent transaction.
 *
 * <p>All {@code count} iterations share <em>one</em> correlation id for grouping — correlation is a
 * tracing field, never an idempotency key, so sharing it does not undermine distinctness.
 *
 * @param count how many times to run the flow (capped by {@code chaos.limits.max-n-times}, and
 *     {@code chaos.limits.max-n-times-sync} when {@code mode == SYNC})
 * @param pacing inter-event timing ({@link Pacing#BURST} no delay, {@link Pacing#LINEAR} fixed gap,
 *     {@link Pacing#RANDOM} random gap)
 * @param mode execution shape ({@link ExecutionMode#SYNC} in-line, {@link ExecutionMode#ASYNC}
 *     run-tracked); {@code null} is treated as {@code SYNC}
 * @param fixedDelayMs fixed inter-event gap in ms; required for {@link Pacing#LINEAR}
 * @param minDelayMs lower bound of the random inter-event gap in ms; required for
 *     {@link Pacing#RANDOM}
 * @param maxDelayMs upper bound of the random inter-event gap in ms; required for
 *     {@link Pacing#RANDOM}
 */
@RecordBuilder
public record NTimesOptions(
    int count,
    @Nullable Pacing pacing,
    @Nullable ExecutionMode mode,
    @Nullable Long fixedDelayMs,
    @Nullable Long minDelayMs,
    @Nullable Long maxDelayMs) {}
