package com.softspark.chaos.flow;

import com.softspark.chaos.flow.model.FlowType;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Registry that maps each {@link FlowType} to its corresponding {@link FlowBuilder}.
 *
 * <p>All {@link FlowBuilder} beans discovered in the Spring context are registered at startup.
 * Builders are expected to be unique per flow type; duplicate registrations will throw an
 * {@link IllegalStateException} at startup.
 */
@Component
public class FlowBuilderRegistry {

  private final Map<FlowType, FlowBuilder<?>> registry;

  /**
   * Constructs the registry from all {@link FlowBuilder} beans in the context.
   *
   * @param builders all discovered flow builders
   * @throws IllegalStateException if two builders register the same flow type
   */
  public FlowBuilderRegistry(List<FlowBuilder<?>> builders) {
    this.registry =
        builders.stream()
            .collect(
                Collectors.toUnmodifiableMap(
                    FlowBuilder::type,
                    Function.identity(),
                    (a, b) -> {
                      throw new IllegalStateException(
                          "Duplicate FlowBuilder for type: " + a.type());
                    }));
  }

  /**
   * Looks up the builder for the given flow type.
   *
   * @param type the flow type to look up
   * @return the registered builder
   * @throws IllegalStateException if no builder is registered for the type
   */
  public FlowBuilder<?> get(FlowType type) {
    FlowBuilder<?> builder = registry.get(type);
    if (builder == null) {
      throw new IllegalStateException("No FlowBuilder registered for: " + type);
    }
    return builder;
  }

  /**
   * Returns an unmodifiable view of all registered builders.
   *
   * @return all registered builders keyed by flow type
   */
  public Map<FlowType, FlowBuilder<?>> all() {
    return registry;
  }

  /**
   * Returns the canonical transaction-request-id field name for a flow type, if it mints a
   * transaction (ADR-025).
   *
   * @param type the flow type
   * @return the request-id field name, or empty for non-transactional flows
   */
  public Optional<String> transactionRequestIdField(FlowType type) {
    return get(type).transactionRequestIdField();
  }

  /**
   * Resolves the concrete transaction-request-id value carried by a request — the value the ledger
   * will file under {@code transactionRequestId}. Single-sourced through the builder's labelled
   * field so the publish response and the {@code publish_record} column agree.
   *
   * @param request the flow request (post-expansion, so N-Times re-minted ids are honoured)
   * @return the request id, or empty when the flow mints no transaction or the field is blank/absent
   */
  public Optional<String> transactionRequestIdValue(FlowRequest request) {
    return transactionRequestIdField(request.flowType())
        .map(field -> request.flowFields().get(field))
        .map(Object::toString)
        .map(String::trim)
        .filter(value -> !value.isEmpty());
  }
}
