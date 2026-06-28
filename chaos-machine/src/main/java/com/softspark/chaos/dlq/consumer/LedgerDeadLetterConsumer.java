package com.softspark.chaos.dlq.consumer;

import com.softspark.chaos.dlq.service.DeadLetterProjectionService;
import com.softspark.chaos.kafka.ConsumerConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * Consumes the ledger's inbound dead-letter topics (the explicit {@code chaos.topics.ledger-dlts}
 * list) and projects each dead letter into the {@code dlq} table (Phase 020 Task 001).
 *
 * <p>Runs on the dedicated {@link ConsumerConfiguration#DLQ_CONTAINER_FACTORY} — a
 * <strong>tolerant, terminal</strong> factory with a log-and-skip handler and no recoverer, so this
 * consumer can never produce a {@code *.dlt.dlt}. The {@code .dlt} record's own topic/partition/
 * offset are read from the Kafka headers and form the dedup key. Its own consumer group keeps it
 * independent of the projection consumers.
 *
 * <p>Gated by {@code chaos.kafka.consumer.enabled}.
 */
@Component
@ConditionalOnProperty(
    prefix = "chaos.kafka.consumer",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class LedgerDeadLetterConsumer {

  private static final Logger log = LoggerFactory.getLogger(LedgerDeadLetterConsumer.class);

  private final DeadLetterProjectionService projectionService;

  public LedgerDeadLetterConsumer(DeadLetterProjectionService projectionService) {
    this.projectionService = projectionService;
  }

  /**
   * Handles a single dead-letter record from one of the configured {@code .dlt} topics.
   *
   * @param record the deserialized {@link LedgerDeadLetterRecord}
   * @param topic the {@code .dlt} topic the record came from
   * @param partition the {@code .dlt} partition
   * @param offset the {@code .dlt} offset
   */
  @KafkaListener(
      topics = "#{@topicCatalog.ledgerDlts}",
      groupId = "${chaos.kafka.consumer.dlq-group-id:chaos-machine-dlq}",
      containerFactory = ConsumerConfiguration.DLQ_CONTAINER_FACTORY)
  public void onDeadLetter(
      LedgerDeadLetterRecord record,
      @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
      @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
      @Header(KafkaHeaders.OFFSET) long offset) {
    if (record == null) {
      log.warn("Received null dead-letter record on {} — ignoring", topic);
      return;
    }
    log.debug("Consuming dead letter from {} (original {})", topic, record.originalTopic());
    projectionService.project(record, topic, partition, offset);
  }
}
