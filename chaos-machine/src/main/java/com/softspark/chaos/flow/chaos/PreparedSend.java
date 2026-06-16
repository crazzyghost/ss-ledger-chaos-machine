package com.softspark.chaos.flow.chaos;

import com.softspark.chaos.kafka.EventEnvelope;
import java.time.Duration;
import org.springframework.lang.Nullable;

/**
 * A single prepared send unit produced by {@link ChaosPlan}.
 *
 * <p>For normal (non-chaos) execution, exactly one instance is produced with a zero delay and null
 * chaos label. Chaos strategies may produce multiple instances or instances with delays/overrides.
 *
 * @param envelope the event envelope to publish (ignored when {@code rawOverride} is set)
 * @param rawOverride pre-serialized JSON string for malformed chaos sends; non-null causes the raw
 *     Kafka template to be used instead of the typed template
 * @param delay artificial delay to apply before publishing (virtual thread sleep)
 * @param chaosLabel human-readable label identifying the active chaos strategy, null for normal
 *     sends
 */
public record PreparedSend(
    EventEnvelope<?> envelope,
    @Nullable String rawOverride,
    Duration delay,
    @Nullable String chaosLabel) {}
