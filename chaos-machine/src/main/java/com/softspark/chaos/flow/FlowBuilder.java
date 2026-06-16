package com.softspark.chaos.flow;

import com.softspark.chaos.flow.model.FlowType;
import com.softspark.chaos.kafka.EventEnvelope;

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
}
