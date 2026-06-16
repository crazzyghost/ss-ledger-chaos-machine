package com.softspark.chaos.history.service;

import com.softspark.chaos.flow.FlowRequest;
import java.util.Map;
import org.springframework.lang.Nullable;

/**
 * Extracts source and destination VA ids from a resolved flow context for history recording.
 *
 * <p>By convention:
 *
 * <ul>
 *   <li>{@code sourceVaId} = {@code resolvedSlots.get("source")}; falls back to
 *       {@code flowFields.get("source_va_id")} or {@code flowFields.get("virtual_account_id")}
 *   <li>{@code destinationVaId} = {@code resolvedSlots.get("destination")}
 * </ul>
 */
class VaIdExtractor {

  private VaIdExtractor() {}

  /**
   * Extracts the source VA id from the request.
   *
   * @param resolvedSlots the resolved slot map from the flow context
   * @param request the originating flow request
   * @return the source VA id, or {@code null} if not determinable
   */
  @Nullable
  static String extractSourceVaId(Map<String, String> resolvedSlots, FlowRequest request) {
    String fromSlot = resolvedSlots.get("source");
    if (fromSlot != null) {
      return fromSlot;
    }
    Object fromFields = request.flowFields().get("source_va_id");
    if (fromFields != null) {
      return fromFields.toString();
    }
    Object vaId = request.flowFields().get("virtual_account_id");
    return vaId != null ? vaId.toString() : null;
  }

  /**
   * Extracts the destination VA id from the request.
   *
   * @param resolvedSlots the resolved slot map from the flow context
   * @param request the originating flow request
   * @return the destination VA id, or {@code null} if not determinable
   */
  @Nullable
  static String extractDestinationVaId(Map<String, String> resolvedSlots, FlowRequest request) {
    String fromSlot = resolvedSlots.get("destination");
    if (fromSlot != null) {
      return fromSlot;
    }
    Object fromFields = request.flowFields().get("destination_va_id");
    return fromFields != null ? fromFields.toString() : null;
  }
}
