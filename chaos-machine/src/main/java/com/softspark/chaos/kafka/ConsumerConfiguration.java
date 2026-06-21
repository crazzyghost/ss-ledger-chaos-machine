package com.softspark.chaos.kafka;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.softspark.chaos.account.consumer.LedgerAccountCreatedEventData;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.util.backoff.ExponentialBackOff;

/**
 * Kafka consumer configuration for the chaos machine — the service's first inbound listener wiring.
 *
 * <p>Provides a {@link ConsumerFactory} and a {@link ConcurrentKafkaListenerContainerFactory} that
 * deserialize the ledger's {@link EventEnvelope}{@code <LedgerAccountCreatedEventData>} payloads.
 * The ledger publishes with {@code spring.json.add.type.headers=false}, so the {@link
 * JsonDeserializer} is pinned to the target {@link JavaType} rather than reading type headers, and
 * wrapped in an {@link ErrorHandlingDeserializer} so malformed bytes never kill the container.
 *
 * <p>A {@link DefaultErrorHandler} retries transient failures with bounded exponential back-off and
 * then routes the record to {@code <topic>.dlt} (reusing the ledger's pre-declared {@code
 * ledger.account.created.dlt}) via a {@link DeadLetterPublishingRecoverer}. Deserialization
 * failures are classified as non-retryable and dead-lettered immediately.
 *
 * <p>The {@code chaos.kafka.consumer.enabled} flag gates the {@code @KafkaListener} beans (see the
 * consumer component), so when disabled no listener container starts even though these factory
 * beans exist.
 */
@Configuration
@EnableConfigurationProperties(ConsumerProperties.class)
public class ConsumerConfiguration {

  /** Bean name of the listener container factory referenced by the ledger-event consumer. */
  public static final String LEDGER_EVENT_CONTAINER_FACTORY = "ledgerEventListenerContainerFactory";

  @Value("${spring.kafka.bootstrap-servers}")
  private String bootstrapServers;

  private final ConsumerProperties consumerProperties;

  public ConsumerConfiguration(ConsumerProperties consumerProperties) {
    this.consumerProperties = consumerProperties;
  }

  /**
   * Builds the consumer factory for ledger events.
   *
   * <p>The value deserializer is an {@link ErrorHandlingDeserializer} wrapping a {@link
   * JsonDeserializer} pinned to {@code EventEnvelope<LedgerAccountCreatedEventData>} using the
   * snake_case-aware {@code kafkaObjectMapper}. Type headers are disabled to match the ledger's
   * producer configuration.
   *
   * @param kafkaObjectMapper the shared object mapper (snake_case records + JavaTime)
   * @return the configured consumer factory
   */
  @Bean
  public ConsumerFactory<String, Object> ledgerEventConsumerFactory(
      ObjectMapper kafkaObjectMapper) {
    Map<String, Object> props = new LinkedHashMap<>();
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    props.put(ConsumerConfig.GROUP_ID_CONFIG, consumerProperties.groupId());
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, consumerProperties.autoOffsetReset());
    props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

    JavaType envelopeType =
        kafkaObjectMapper
            .getTypeFactory()
            .constructParametricType(EventEnvelope.class, LedgerAccountCreatedEventData.class);

    JsonDeserializer<Object> jsonDeserializer =
        new JsonDeserializer<>(envelopeType, kafkaObjectMapper, false);
    jsonDeserializer.setUseTypeHeaders(false);
    jsonDeserializer.addTrustedPackages("com.softspark.chaos.*");

    var keyDeserializer = new ErrorHandlingDeserializer<>(new StringDeserializer());
    var valueDeserializer = new ErrorHandlingDeserializer<>(jsonDeserializer);

    return new DefaultKafkaConsumerFactory<>(props, keyDeserializer, valueDeserializer);
  }

  /**
   * The listener container factory used by the ledger-event consumer.
   *
   * @param ledgerEventConsumerFactory the consumer factory
   * @param errorHandler               the shared retry + DLT error handler
   * @return the container factory
   */
  @Bean(LEDGER_EVENT_CONTAINER_FACTORY)
  public ConcurrentKafkaListenerContainerFactory<String, Object>
      ledgerEventListenerContainerFactory(
          ConsumerFactory<String, Object> ledgerEventConsumerFactory,
          DefaultErrorHandler ledgerEventErrorHandler) {
    var factory = new ConcurrentKafkaListenerContainerFactory<String, Object>();
    factory.setConsumerFactory(ledgerEventConsumerFactory);
    factory.setConcurrency(consumerProperties.concurrency());
    factory.setCommonErrorHandler(ledgerEventErrorHandler);
    return factory;
  }

  /**
   * Error handler: bounded exponential back-off retry, then dead-letter to {@code <topic>.dlt}.
   *
   * @param recoverer the dead-letter publishing recoverer
   * @return the configured error handler
   */
  @Bean
  public DefaultErrorHandler ledgerEventErrorHandler(DeadLetterPublishingRecoverer recoverer) {
    var backOff = new ExponentialBackOff();
    backOff.setInitialInterval(consumerProperties.backoffInitialMs());
    backOff.setMultiplier(consumerProperties.backoffMultiplier());
    // maxAttempts counts retries after the first delivery, so subtract the initial attempt.
    backOff.setMaxAttempts(Math.max(0, consumerProperties.maxAttempts() - 1));

    var handler = new DefaultErrorHandler(recoverer, backOff);
    // Bad bytes can never succeed on retry — dead-letter immediately.
    handler.addNotRetryableExceptions(DeserializationException.class);
    return handler;
  }

  /**
   * Publishes failed/exhausted records to {@code <source-topic>.dlt}.
   *
   * <p>Two templates are registered so both deserialization failures (raw {@code byte[]} recovered
   * from the {@link ErrorHandlingDeserializer} header) and handler failures on well-formed records
   * (serialized via JSON) can be republished. A negative target partition lets the producer's
   * partitioner choose, so the DLT need not match the source partition count.
   *
   * @param kafkaTemplate the JSON object template (from the producer configuration)
   * @return the recoverer
   */
  @Bean
  public DeadLetterPublishingRecoverer deadLetterPublishingRecoverer(
      KafkaTemplate<String, Object> kafkaTemplate) {
    var bytesTemplate =
        new KafkaTemplate<>(
            new DefaultKafkaProducerFactory<byte[], byte[]>(
                Map.of(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers),
                new ByteArraySerializer(),
                new ByteArraySerializer()));

    Map<Class<?>, KafkaOperations<?, ?>> templates = new LinkedHashMap<>();
    templates.put(byte[].class, bytesTemplate);
    templates.put(Object.class, kafkaTemplate);

    return new DeadLetterPublishingRecoverer(
        templates, (record, ex) -> new TopicPartition(record.topic() + ".dlt", -1));
  }
}
