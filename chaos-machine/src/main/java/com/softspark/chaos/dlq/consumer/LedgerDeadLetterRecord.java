package com.softspark.chaos.dlq.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.Instant;
import java.util.UUID;

/**
 * Mirror of the ledger's {@code DeadLetterTopicRecord} (snake_case JSON) published to each {@code
 * ledger.<flow>.dlt} topic when the ledger fails to consume an inbound event after its retries
 * (ADR-029).
 *
 * <p>The wrapper is always valid JSON even when {@code originalEvent} is {@code null} (the original
 * payload was unparseable — a {@code DESERIALIZATION}-class failure). {@code originalEvent} is held
 * as a raw {@link JsonNode} (the ledger's {@code EventEnvelope<JsonNode>}) so it can be re-serialized
 * for the Message tab and best-effort mined for the transaction id/type.
 *
 * @param deadLetterId the dead-letter id
 * @param deadLetteredAt when the ledger dead-lettered the record
 * @param originalTopic the original inbound topic the ledger consumed (e.g. {@code collection.completed})
 * @param originalPartition the original record's partition
 * @param originalOffset the original record's offset
 * @param originalKey the original record's key
 * @param failure the failure detail (classification, exception type, message, retry count)
 * @param originalEvent the original event envelope as a JSON node (nullable when unparseable)
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record LedgerDeadLetterRecord(
    UUID deadLetterId,
    Instant deadLetteredAt,
    String originalTopic,
    Integer originalPartition,
    Long originalOffset,
    String originalKey,
    Failure failure,
    JsonNode originalEvent) {

  /**
   * The ledger's {@code Failure} sub-record.
   *
   * @param classification PROCESSING | DESERIALIZATION | VERSION_RESOLUTION
   * @param exceptionType the exception class (the "error code")
   * @param message the failure message (the "error reason")
   * @param retryCount the number of retries before dead-lettering
   */
  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public record Failure(
      String classification, String exceptionType, String message, Integer retryCount) {}
}
