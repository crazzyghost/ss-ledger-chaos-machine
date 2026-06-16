package com.softspark.chaos.flow;

import com.softspark.chaos.flow.model.FlowType;
import java.util.List;
import java.util.Map;
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
}
