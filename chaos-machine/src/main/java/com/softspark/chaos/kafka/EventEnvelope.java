package com.softspark.chaos.kafka;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.soabase.recordbuilder.core.RecordBuilder;
import java.time.Instant;

/**
 * Event envelope for all Kafka messages published by the chaos machine.
 * <p>
 * This envelope wraps domain event payloads with standard metadata fields required
 * by the ledger service. The structure and field names match the ledger's expectations
 * exactly, ensuring byte-compatibility.
 *
 * @param eventId   unique identifier for this event (ULID)
 * @param eventType the type of event (e.g., "organization.onboarded")
 * @param timestamp the event creation timestamp (ISO-8601 UTC)
 * @param source    the source system that generated the event
 * @param version   the event schema version
 * @param data      the event payload
 * @param metadata  additional correlation and idempotency metadata
 * @param <T>       the type of the event payload
 */
@RecordBuilder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record EventEnvelope<T>(
    @JsonProperty("event_id") String eventId,
    @JsonProperty("event_type") String eventType,
    Instant timestamp,
    String source,
    String version,
    T data,
    EventMetadata metadata) {}
