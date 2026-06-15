package com.softspark.chaos.kafka;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

/**
 * High-level publisher for chaos events.
 * <p>
 * Wraps the Kafka template with metrics, logging, and error handling. All event
 * publishing should go through this component to ensure consistent behavior.
 */
@Component
public class ChaosEventPublisher {

  private static final Logger log = LoggerFactory.getLogger(ChaosEventPublisher.class);

  private final KafkaTemplate<String, Object> kafkaTemplate;
  private final MeterRegistry meterRegistry;

  public ChaosEventPublisher(
      KafkaTemplate<String, Object> kafkaTemplate, MeterRegistry meterRegistry) {
    this.kafkaTemplate = kafkaTemplate;
    this.meterRegistry = meterRegistry;
  }

  /**
   * Publishes an event envelope to the specified topic.
   *
   * @param topic    the Kafka topic name
   * @param key      the message key (typically an aggregate ID)
   * @param envelope the event envelope to publish
   * @return a PublishResult with the broker offset and partition on success
   * @throws EventPublishException if publishing fails
   */
  public PublishResult publish(String topic, String key, EventEnvelope<?> envelope) {
    long startTime = System.nanoTime();
    try {
      CompletableFuture<SendResult<String, Object>> future =
          kafkaTemplate.send(new ProducerRecord<>(topic, key, envelope));
      SendResult<String, Object> result = future.join();
      RecordMetadata metadata = result.getRecordMetadata();

      long durationMs = (System.nanoTime() - startTime) / 1_000_000;
      meterRegistry
          .counter(
              "chaos.events.published",
              "topic",
              topic,
              "event_type",
              envelope.eventType(),
              "status",
              "success")
          .increment();
      meterRegistry
          .timer("chaos.events.publish.duration", "topic", topic)
          .record(durationMs, java.util.concurrent.TimeUnit.MILLISECONDS);

      log.info(
          "Published event {} to topic {} partition {} offset {}",
          envelope.eventId(),
          topic,
          metadata.partition(),
          metadata.offset());

      return new PublishResult(metadata.offset(), metadata.partition());
    } catch (Exception e) {
      meterRegistry
          .counter(
              "chaos.events.published",
              "topic",
              topic,
              "event_type",
              envelope.eventType(),
              "status",
              "failure")
          .increment();
      log.error(
          "Failed to publish event {} to topic {}: {}",
          envelope.eventId(),
          topic,
          e.getMessage(),
          e);
      throw new EventPublishException("Failed to publish event to topic " + topic, e);
    }
  }

  /**
   * Result of a successful publish operation.
   *
   * @param offset    the broker offset where the message was stored
   * @param partition the partition where the message was stored
   */
  public record PublishResult(long offset, int partition) {}
}
