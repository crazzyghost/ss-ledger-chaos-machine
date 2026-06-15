package com.softspark.chaos.health;

import io.soabase.recordbuilder.core.RecordBuilder;

import java.time.Instant;

/**
 * Response for the health status endpoint.
 *
 * @param status      the health status ("UP" or "DOWN")
 * @param timestamp   the current server timestamp
 * @param clusterLabel the configured Kafka cluster label
 */
@RecordBuilder
public record HealthResponse(
        String status,
        Instant timestamp,
        String clusterLabel
) {
}
