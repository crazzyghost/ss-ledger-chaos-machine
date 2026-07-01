package com.softspark.chaos.run.dto;

import java.time.Instant;
import org.springframework.lang.Nullable;

/**
 * Lightweight projection of a single untracked {@code publish_record} row (one with
 * {@code batch_id IS NULL}), fetched by {@code PublishRecordRepository.findUntrackedRunRows} and
 * grouped in memory by the run-summary service.
 *
 * <p>Only the columns needed to roll up an untracked run are selected (no {@code payload_json} TEXT),
 * and {@code createdAt} is materialised as an {@link Instant} by the SELECT rather than aggregated in
 * SQL — so min/max are computed on real instants in Java (the stored value is a string via
 * {@code InstantStringConverter}, whose lexicographic order is not chronological once fractional
 * seconds appear).
 *
 * @param id the publish-record id (the run key for a null-correlation singleton)
 * @param correlationId the grouping key; {@code null} rows each form their own singleton run
 * @param eventType the published event type (contributes to the run's distinct flow types)
 * @param status {@code "PUBLISHED"} or {@code "FAILED"}
 * @param intentionalFailure whether the event was an intentional/chaos failure
 * @param createdAt when the event was recorded
 */
public record RunGroupRow(
    String id,
    @Nullable String correlationId,
    @Nullable String eventType,
    @Nullable String status,
    boolean intentionalFailure,
    Instant createdAt) {}
