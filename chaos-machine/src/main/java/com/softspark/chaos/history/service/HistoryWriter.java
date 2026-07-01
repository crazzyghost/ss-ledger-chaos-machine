package com.softspark.chaos.history.service;

import com.softspark.chaos.flow.FlowRequest;
import com.softspark.chaos.kafka.ChaosEventPublisher;
import com.softspark.chaos.kafka.EventEnvelope;
import org.springframework.lang.Nullable;

/**
 * Seam for recording publish history entries.
 *
 * <p>Implementations must return the pre-generated history record id immediately (non-blocking)
 * and persist the record asynchronously to avoid blocking the Kafka publish path.
 */
public interface HistoryWriter {

  /**
   * Records a successful publish event.
   *
   * @param envelope the published event envelope
   * @param topic the Kafka topic
   * @param partitionKey the Kafka message key
   * @param publishResult the broker acknowledgement
   * @param request the originating flow request
   * @param chaosLabel the chaos strategy label; {@code null} for normal sends
   * @param intentionalFailure {@code true} when this record represents a deliberate failure (e.g.,
   *     a chaos test sending a known-bad payload)
   * @return the pre-generated history record ULID
   */
  String record(
      EventEnvelope<?> envelope,
      String topic,
      String partitionKey,
      ChaosEventPublisher.PublishResult publishResult,
      FlowRequest request,
      @Nullable String chaosLabel,
      boolean intentionalFailure);

  /**
   * Records a failed publish attempt.
   *
   * @param envelope the event envelope that failed to publish
   * @param topic the Kafka topic
   * @param errorMsg the publisher error message
   * @param request the originating flow request
   * @return the pre-generated history record ULID
   */
  String recordFailure(
      EventEnvelope<?> envelope, String topic, String errorMsg, FlowRequest request);

  /**
   * Records a failed publish attempt that belongs to a tracked run, stamping the run/row linkage so
   * the failed event groups under its {@code batch_run} in the {@code /runs} feed (rather than
   * leaking as a stray untracked run).
   *
   * @param envelope the event envelope that failed to publish
   * @param topic the Kafka topic
   * @param errorMsg the publisher error message
   * @param request the originating flow request
   * @param batchId the tracked run identifier
   * @param batchRowId the originating run-row identifier, or {@code null}
   * @return the pre-generated history record ULID
   */
  String recordBatchFailure(
      EventEnvelope<?> envelope,
      String topic,
      String errorMsg,
      FlowRequest request,
      String batchId,
      @Nullable String batchRowId);

  /**
   * Records a publish event that originated from a CSV batch run.
   *
   * @param envelope the published event envelope
   * @param topic the Kafka topic
   * @param partitionKey the Kafka message key
   * @param publishResult the broker acknowledgement
   * @param request the originating flow request
   * @param batchId the batch run identifier
   * @param batchRowId the batch row identifier
   * @param chaosLabel the chaos strategy label; {@code null} for normal sends
   * @param intentionalFailure {@code true} for deliberate failure records
   * @return the pre-generated history record ULID
   */
  String recordBatch(
      EventEnvelope<?> envelope,
      String topic,
      String partitionKey,
      ChaosEventPublisher.PublishResult publishResult,
      FlowRequest request,
      String batchId,
      String batchRowId,
      @Nullable String chaosLabel,
      boolean intentionalFailure);
}
