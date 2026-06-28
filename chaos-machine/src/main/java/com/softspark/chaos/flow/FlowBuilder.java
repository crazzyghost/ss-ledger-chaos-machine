package com.softspark.chaos.flow;

import com.softspark.chaos.flow.model.FlowType;
import com.softspark.chaos.kafka.EventEnvelope;
import java.util.Optional;

/**
 * Strategy interface for building a typed {@link EventEnvelope} for a specific {@link FlowType}.
 *
 * <p>Each implementation is a Spring-managed {@code @Component} and is registered automatically in
 * {@link FlowBuilderRegistry}.
 *
 * @param <T> the event payload type produced by this builder
 */
public interface FlowBuilder<T> {

  /**
   * Returns the {@link FlowType} this builder handles.
   *
   * @return the flow type
   */
  FlowType type();

  /**
   * Returns the source system string embedded in the event envelope.
   *
   * @return the source identifier (e.g., {@code "payments-service"})
   */
  String source();

  /**
   * Builds the typed {@link EventEnvelope} from the given request and resolved context.
   *
   * @param request the originating flow request
   * @param ctx the resolved execution context
   * @return the constructed event envelope ready for publishing
   */
  EventEnvelope<T> build(FlowRequest request, FlowContext ctx);

  /**
   * Determines the Kafka partition key for the event.
   *
   * <p>Typically an aggregate identifier (organization ID or VA ID) that ensures related events
   * land on the same partition.
   *
   * @param ctx the resolved execution context
   * @return the Kafka message key
   */
  String partitionKey(FlowContext ctx);

  /**
   * Names the payload field that carries this flow's canonical <em>transaction request id</em> — the
   * value the ledger files under {@code transactionRequestId} and that {@code
   * ledger.transaction.failed} / {@code ledger.reservation.*} events echo back for correlation
   * (ADR-025). It is the same field the builder reads to populate the payload (e.g. {@code
   * transaction_id}, {@code settlement_request_id}, {@code batch_id}).
   *
   * <p>Defaults to {@link Optional#empty()} for flows that mint no transaction (onboarding,
   * va-updated); such flows are never armed for downstream failure/reservation correlation.
   *
   * @return the request-id field name, or empty for non-transactional flows
   */
  default Optional<String> transactionRequestIdField() {
    return Optional.empty();
  }
}
