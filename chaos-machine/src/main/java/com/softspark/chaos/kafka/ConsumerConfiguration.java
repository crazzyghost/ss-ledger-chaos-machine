package com.softspark.chaos.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
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
import org.springframework.kafka.listener.CommonLoggingErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.converter.ByteArrayJsonMessageConverter;
import org.springframework.kafka.support.converter.ConversionException;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.messaging.converter.MessageConversionException;
import org.springframework.util.backoff.ExponentialBackOff;

/**
 * Kafka consumer configuration for the chaos machine — the shared wiring for every inbound
 * ledger-outbound listener.
 *
 * <p>A <strong>single</strong> {@link ConcurrentKafkaListenerContainerFactory} serves all ledger
 * outbound events. Rather than pinning the value deserializer to one payload type (the Phase 009
 * approach), the value deserializer reads <em>raw bytes</em> and a {@link
 * ByteArrayJsonMessageConverter} (built from the shared {@code kafkaObjectMapper}) converts each
 * record into the type declared by the {@code @KafkaListener} method's {@link EventEnvelope}{@code
 * <T>} parameter. The ledger publishes with {@code spring.json.add.type.headers=false}, so the
 * converter's method-driven target type is what lets multiple payload types coexist on one factory
 * (ADR-024). Adding a new ledger-outbound listener is then a listener method + a mirror record —
 * no Kafka-config change.
 *
 * <p>A {@link DefaultErrorHandler} retries transient failures with bounded exponential back-off and
 * then routes the record to {@code <topic>.dlt} via a {@link DeadLetterPublishingRecoverer} (the
 * DLT is derived from the failed record's topic, so it serves every listener with no per-event
 * config). Deserialization <em>and conversion</em> failures are non-retryable: a malformed/poison
 * JSON body can never parse, so it dead-letters immediately instead of retrying.
 *
 * <p>The {@code chaos.kafka.consumer.enabled} flag gates the {@code @KafkaListener} beans (see the
 * consumer components), so when disabled no listener container starts even though these factory
 * beans exist.
 */
@Configuration
@EnableConfigurationProperties(ConsumerProperties.class)
public class ConsumerConfiguration {

  /** Bean name of the listener container factory referenced by every ledger-event consumer. */
  public static final String LEDGER_EVENT_CONTAINER_FACTORY = "ledgerEventListenerContainerFactory";

  /**
   * Bean name of the dedicated dead-letter-queue listener container factory (Phase 020). It is
   * <strong>tolerant and terminal</strong>: a log-and-skip error handler with <em>no</em>
   * recoverer, so a DLT viewer can never itself create a {@code *.dlt.dlt}.
   */
  public static final String DLQ_CONTAINER_FACTORY = "dlqListenerContainerFactory";

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
   * ByteArrayDeserializer}: raw bytes never fail to deserialize, so the JSON→object step moves into
   * the {@link ByteArrayJsonMessageConverter} on the container factory, where a malformed payload
   * surfaces as a conversion failure during listener invocation (and is dead-lettered immediately).
   *
   * @return the configured byte-array consumer factory
   */
  @Bean
  public ConsumerFactory<String, byte[]> ledgerEventConsumerFactory() {
    Map<String, Object> props = new LinkedHashMap<>();
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    props.put(ConsumerConfig.GROUP_ID_CONFIG, consumerProperties.groupId());
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, consumerProperties.autoOffsetReset());
    props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

    var keyDeserializer = new ErrorHandlingDeserializer<>(new StringDeserializer());
    var valueDeserializer = new ErrorHandlingDeserializer<>(new ByteArrayDeserializer());

    return new DefaultKafkaConsumerFactory<>(props, keyDeserializer, valueDeserializer);
  }

  /**
   * The single listener container factory used by every ledger-event consumer.
   *
   * <p>The {@link ByteArrayJsonMessageConverter} resolves its conversion target type from each
   * {@code @KafkaListener} method's generic {@link EventEnvelope}{@code <T>} parameter, so one
   * factory serves listeners for {@code ledger.account.created}, {@code ledger.transaction.failed},
   * and every later part of the series. The shared {@code kafkaObjectMapper} (snake_case records +
   * JavaTime) matches the producer.
   *
   * @param ledgerEventConsumerFactory the byte-array consumer factory
   * @param ledgerEventErrorHandler the shared retry + DLT error handler
   * @param kafkaObjectMapper the shared object mapper (snake_case records + JavaTime)
   * @return the container factory
   */
  // ByteArrayJsonMessageConverter is the Jackson-2 converter that pairs with the project-wide
  // Jackson-2 kafkaObjectMapper (the event records, producer, and prior deserializer are all
  // Jackson 2). Spring Kafka 4 marks it for removal in favour of the Jackson-3 variant; migrating
  // the whole service to Jackson 3 is out of scope, so the deprecation is suppressed deliberately.
  @SuppressWarnings("removal")
  @Bean(LEDGER_EVENT_CONTAINER_FACTORY)
  public ConcurrentKafkaListenerContainerFactory<String, byte[]>
      ledgerEventListenerContainerFactory(
          ConsumerFactory<String, byte[]> ledgerEventConsumerFactory,
          DefaultErrorHandler ledgerEventErrorHandler,
          ObjectMapper kafkaObjectMapper) {
    var factory = new ConcurrentKafkaListenerContainerFactory<String, byte[]>();
    factory.setConsumerFactory(ledgerEventConsumerFactory);
    factory.setRecordMessageConverter(new ByteArrayJsonMessageConverter(kafkaObjectMapper));
    factory.setConcurrency(consumerProperties.concurrency());
    factory.setCommonErrorHandler(ledgerEventErrorHandler);
    return factory;
  }

  /**
   * The dead-letter-queue listener container factory (Phase 020, ADR-029).
   *
   * <p>Reuses the byte-array consumer factory + the {@link ByteArrayJsonMessageConverter} (so the
   * DLT record JSON converts to the listener's {@code LedgerDeadLetterRecord} parameter), but runs a
   * {@link CommonLoggingErrorHandler} — it <strong>logs and skips</strong> a record it cannot
   * project and has <strong>no recoverer</strong>, so it never produces to any topic (no {@code
   * *.dlt.dlt}). The DLT consumer uses its own group id (set on the listener).
   *
   * @param ledgerEventConsumerFactory the shared byte-array consumer factory
   * @param kafkaObjectMapper the shared object mapper (snake_case records + JavaTime)
   * @return the DLQ container factory
   */
  @SuppressWarnings("removal")
  @Bean(DLQ_CONTAINER_FACTORY)
  public ConcurrentKafkaListenerContainerFactory<String, byte[]> dlqListenerContainerFactory(
      ConsumerFactory<String, byte[]> ledgerEventConsumerFactory, ObjectMapper kafkaObjectMapper) {
    var factory = new ConcurrentKafkaListenerContainerFactory<String, byte[]>();
    factory.setConsumerFactory(ledgerEventConsumerFactory);
    factory.setRecordMessageConverter(new ByteArrayJsonMessageConverter(kafkaObjectMapper));
    factory.setConcurrency(consumerProperties.concurrency());
    factory.setCommonErrorHandler(new CommonLoggingErrorHandler());
    return factory;
  }

  /**
   * Error handler: bounded exponential back-off retry, then dead-letter to {@code <topic>.dlt}.
   *
   * <p>Both deserialization failures (bad key bytes) and <em>conversion</em> failures (a JSON body
   * that cannot be mapped to the listener's declared type) are non-retryable — they can never
   * succeed on retry — so they dead-letter immediately rather than exhausting the back-off.
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
    // Bad/poison bytes can never succeed on retry — dead-letter immediately. Conversion now
    // happens in the message converter (not the deserializer), so its exceptions must be listed
    // alongside DeserializationException for the same immediate-DLT behaviour.
    handler.addNotRetryableExceptions(
        DeserializationException.class,
        MessageConversionException.class,
        ConversionException.class);
    return handler;
  }

  /**
   * Publishes failed/exhausted records to {@code <source-topic>.dlt}.
   *
   * <p>Two templates are registered so both byte-array values (the raw bytes recovered after a
   * conversion failure) and well-formed object values can be republished. A negative target
   * partition lets the producer's partitioner choose, so the DLT need not match the source
   * partition count. The {@code <topic>.dlt} derivation serves every ledger-outbound listener with
   * no per-event configuration.
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
        templates, (record, ex) -> new TopicPartition(TopicCatalog.dltFor(record.topic()), -1));
  }
}
