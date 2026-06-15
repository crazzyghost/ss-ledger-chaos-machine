package com.softspark.chaos.kafka;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.soabase.recordbuilder.core.RecordBuilder;

/**
 * Metadata for event correlation and idempotency.
 * <p>
 * Included in every {@link EventEnvelope} to support request tracing, deduplication,
 * and multi-tenant isolation.
 *
 * @param correlationId  identifier linking related events (typically the originating request ID)
 * @param idempotencyKey key for deduplicating this event (ULID or client-provided)
 * @param tenantId       the tenant or organization this event belongs to
 */
@RecordBuilder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record EventMetadata(
        @JsonProperty("correlation_id") String correlationId,
        @JsonProperty("idempotency_key") String idempotencyKey,
        @JsonProperty("tenant_id") String tenantId
) {
}
