package com.softspark.chaos.flow;

import io.soabase.recordbuilder.core.RecordBuilder;
import org.springframework.lang.Nullable;

/**
 * Result of a single flow execution returned by {@link FlowEngine#execute(FlowRequest)}.
 *
 * @param eventId the ULID of the published event
 * @param topic the Kafka topic the event was published to
 * @param partition the Kafka partition offset
 * @param offset the Kafka log offset; {@code -1} on failure
 * @param status {@code "PUBLISHED"} or {@code "FAILED"}
 * @param historyId the ULID of the persisted {@code PublishRecord}
 * @param error human-readable error message when {@code status} is {@code "FAILED"}; null otherwise
 * @param transactionRequestId the request id the ledger will file under {@code transactionRequestId}
 *     (the correlation key for failure/reservation watching); null for non-transactional flows
 */
@RecordBuilder
public record FlowResult(
    String eventId,
    String topic,
    int partition,
    long offset,
    String status,
    String historyId,
    @Nullable String error,
    @Nullable String transactionRequestId) {}
