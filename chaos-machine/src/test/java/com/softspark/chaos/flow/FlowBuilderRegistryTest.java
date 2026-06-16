package com.softspark.chaos.flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.softspark.chaos.flow.model.FlowType;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link FlowBuilderRegistry}.
 */
@DisplayName("FlowBuilderRegistry")
class FlowBuilderRegistryTest {

  @Test
  @DisplayName("all 12 FlowType values have a registered builder")
  @SuppressWarnings({"unchecked", "rawtypes"})
  void allFlowTypesHaveBuilder() {
    List<FlowBuilder<?>> builders = new ArrayList<>();
    for (FlowType type : FlowType.values()) {
      FlowBuilder builder = mock(FlowBuilder.class);
      when(builder.type()).thenReturn(type);
      builders.add(builder);
    }

    var registry = new FlowBuilderRegistry(builders);

    assertThat(FlowType.values()).hasSize(12);
    for (FlowType type : FlowType.values()) {
      assertThat(registry.get(type)).isNotNull();
    }
    assertThat(registry.all()).hasSize(12);
  }

  @Test
  @DisplayName("get throws IllegalStateException for unregistered type")
  void getThrowsForUnregisteredType() {
    var registry = new FlowBuilderRegistry(List.of());

    org.junit.jupiter.api.Assertions.assertThrows(
        IllegalStateException.class, () -> registry.get(FlowType.COLLECTION_COMPLETED));
  }
}
