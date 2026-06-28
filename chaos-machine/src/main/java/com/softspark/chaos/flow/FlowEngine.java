package com.softspark.chaos.flow;

import com.softspark.chaos.base.Ids;
import com.softspark.chaos.flow.chaos.ChaosPlan;
import com.softspark.chaos.flow.chaos.PreparedSend;
import com.softspark.chaos.flow.resolver.SlotResolver;
import com.softspark.chaos.history.service.HistoryWriter;
import com.softspark.chaos.kafka.ChaosEventPublisher;
import com.softspark.chaos.kafka.EventPublishException;
import com.softspark.chaos.kafka.TopicCatalog;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Orchestrates the full lifecycle of a single transaction flow execution.
 *
 * <p>The execution pipeline is:
 *
 * <ol>
 *   <li>Look up the registered {@link FlowBuilder} for the requested flow type.
 *   <li>Resolve all configured slot VA ids via {@link SlotResolver}.
 *   <li>Build the {@link FlowContext} with generated identifiers and resolved slots.
 *   <li>Build the typed {@link com.softspark.chaos.kafka.EventEnvelope} via the builder.
 *   <li>Expand the envelope into one or more {@link PreparedSend} units via {@link ChaosPlan}.
 *   <li>For each prepared send: apply any delay, publish to Kafka, and record history.
 *   <li>Return the {@link FlowResult} of the last send.
 * </ol>
 */
@Component
public class FlowEngine {

  private static final Logger log = LoggerFactory.getLogger(FlowEngine.class);

  private final FlowBuilderRegistry registry;
  private final SlotResolver slotResolver;
  private final ChaosPlan chaosPlan;
  private final ChaosEventPublisher publisher;
  private final HistoryWriter historyWriter;
  private final TopicCatalog topicCatalog;
  private final String defaultTenantId;

  public FlowEngine(
      FlowBuilderRegistry registry,
      SlotResolver slotResolver,
      ChaosPlan chaosPlan,
      ChaosEventPublisher publisher,
      HistoryWriter historyWriter,
      TopicCatalog topicCatalog,
      @Value("${chaos.default-tenant-id:org_123}") String defaultTenantId) {
    this.registry = registry;
    this.slotResolver = slotResolver;
    this.chaosPlan = chaosPlan;
    this.publisher = publisher;
    this.historyWriter = historyWriter;
    this.topicCatalog = topicCatalog;
    this.defaultTenantId = defaultTenantId;
  }

  /**
   * Executes a transaction flow: resolves slots, builds the envelope, applies chaos, publishes, and
   * records history.
   *
   * @param request the flow execution request
   * @return the result of the last publish attempt; never null
   */
  public FlowResult execute(FlowRequest request) {
    return execute(request, null);
  }

  /**
   * Executes a transaction flow, optionally overriding the chaos label recorded in publish history.
   *
   * <p>When {@code chaosLabelOverride} is non-null it replaces the per-send chaos label written to
   * history (used by the N-Times runners to stamp {@code "NTIMES:<pacing>:i/N"} on each iteration's
   * record); the underlying publish path is otherwise unchanged.
   *
   * @param request the flow execution request
   * @param chaosLabelOverride the chaos label to record, or {@code null} to use the per-send label
   * @return the result of the last publish attempt; never null
   */
  @SuppressWarnings("unchecked")
  public FlowResult execute(
      FlowRequest request, @org.springframework.lang.Nullable String chaosLabelOverride) {
    FlowBuilder<Object> builder = (FlowBuilder<Object>) registry.get(request.flowType());
    var resolvedSlots = slotResolver.resolveAll(request.flowType(), request);
    var ctx =
        FlowContextBuilder.builder()
            .eventId(Ids.generate())
            .timestamp(Instant.now())
            .source(builder.source())
            .tenantId(request.tenantId() != null ? request.tenantId() : defaultTenantId)
            .correlationId(
                request.correlationId() != null ? request.correlationId() : Ids.generate())
            .resolvedSlots(resolvedSlots)
            .request(request)
            .build();

    var envelope = builder.build(request, ctx);
    List<PreparedSend> sends = chaosPlan.expand(envelope, ctx, request.chaos());
    String topic = topicCatalog.topicFor(request.flowType());
    String partitionKey = builder.partitionKey(ctx);
    // The request id is intrinsic to the request (same for all sends of one execution); resolved
    // once and echoed on the result so the client can watch for a matching ledger failure (ADR-025).
    String transactionRequestId = registry.transactionRequestIdValue(request).orElse(null);

    FlowResult result = null;
    for (PreparedSend send : sends) {
      applyDelay(send);

      try {
        ChaosEventPublisher.PublishResult published;
        if (send.rawOverride() != null) {
          published = publisher.publishRaw(topic, partitionKey, send.rawOverride());
        } else {
          published = publisher.publish(topic, partitionKey, send.envelope());
        }

        String chaosLabel = chaosLabelOverride != null ? chaosLabelOverride : send.chaosLabel();
        String historyId =
            historyWriter.record(
                send.envelope(),
                topic,
                partitionKey,
                published,
                request,
                chaosLabel,
                send.chaosLabel() != null && send.chaosLabel().startsWith("MALFORMED"));

        result =
            new FlowResult(
                ctx.eventId(),
                topic,
                published.partition(),
                published.offset(),
                "PUBLISHED",
                historyId,
                null,
                transactionRequestId);
      } catch (EventPublishException e) {
        log.error(
            "Failed to publish flow {} event {}: {}",
            request.flowType(),
            ctx.eventId(),
            e.getMessage(),
            e);
        String historyId =
            historyWriter.recordFailure(send.envelope(), topic, e.getMessage(), request);
        result =
            new FlowResult(
                ctx.eventId(),
                topic,
                -1,
                -1L,
                "FAILED",
                historyId,
                e.getMessage(),
                transactionRequestId);
      }
    }

    return result;
  }

  private void applyDelay(PreparedSend send) {
    if (!send.delay().isZero()) {
      try {
        Thread.sleep(send.delay());
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        log.warn("Chaos delay interrupted for event: {}", send.envelope().eventId());
      }
    }
  }
}
