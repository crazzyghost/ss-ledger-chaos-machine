package com.softspark.chaos.flow.dto;

import com.softspark.chaos.flow.chaos.ChaosOptions;
import io.soabase.recordbuilder.core.RecordBuilder;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.lang.Nullable;

/**
 * REST request body for {@code POST /api/v0/flows/{flowType}}.
 *
 * @param correlationId optional correlation id; auto-generated if null
 * @param tenantId optional tenant override; uses configured default if null
 * @param channel optional payment channel for channel-aware slot resolution
 * @param amount general-purpose monetary amount
 * @param grossAmount gross amount before fee deductions
 * @param netAmount net amount after fee deductions
 * @param currency ISO-4217 currency code
 * @param slotOverrides explicit VA id overrides keyed by slot name; defaults to empty map
 * @param chaos optional chaos injection configuration
 * @param flowFields flow-specific fields keyed by snake_case name; defaults to empty map
 * @param fees optional typed fee rows for fee-bearing flows (collection, disbursement-completed);
 *     defaults to an empty list
 */
@RecordBuilder
public record PublishFlowRequest(
    @Nullable String correlationId,
    @Nullable String tenantId,
    @Nullable String channel,
    @Nullable BigDecimal amount,
    @Nullable BigDecimal grossAmount,
    @Nullable BigDecimal netAmount,
    @Nullable String currency,
    Map<String, String> slotOverrides,
    @Nullable ChaosOptions chaos,
    Map<String, Object> flowFields,
    List<FeeInput> fees) {

  public PublishFlowRequest {
    if (slotOverrides == null) {
      slotOverrides = Map.of();
    }
    if (flowFields == null) {
      flowFields = Map.of();
    }
    if (fees == null) {
      fees = List.of();
    }
  }
}
