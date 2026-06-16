package com.softspark.chaos.flow;

import io.soabase.recordbuilder.core.RecordBuilder;
import java.time.Instant;
import java.util.Map;

/**
 * Resolved execution context passed to {@link FlowBuilder#build(FlowRequest, FlowContext)}.
 *
 * <p>Built by the {@link FlowEngine} after slot resolution. Carries identifiers and resolved
 * account slot assignments that builders use to construct event envelopes.
 *
 * @param eventId unique ULID for the event being published
 * @param timestamp the event creation timestamp
 * @param source the source system identifier (e.g., "payments-service")
 * @param tenantId the effective tenant identifier
 * @param correlationId the effective correlation identifier
 * @param resolvedSlots resolved slot-name-to-VA-id mapping for this flow execution
 * @param request the originating flow request
 */
@RecordBuilder
public record FlowContext(
    String eventId,
    Instant timestamp,
    String source,
    String tenantId,
    String correlationId,
    Map<String, String> resolvedSlots,
    FlowRequest request) {}
