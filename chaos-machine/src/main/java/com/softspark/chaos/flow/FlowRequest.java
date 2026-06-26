package com.softspark.chaos.flow;

import com.softspark.chaos.flow.chaos.ChaosOptions;
import com.softspark.chaos.flow.dto.FeeInput;
import com.softspark.chaos.flow.model.FlowType;
import io.soabase.recordbuilder.core.RecordBuilder;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.lang.Nullable;

/**
 * Request to execute a single transaction flow through the flow engine.
 *
 * <p>Carries all parameters needed to resolve slots, build an event envelope, and optionally apply
 * chaos injection strategies before publishing.
 *
 * @param flowType the type of flow to execute
 * @param correlationId optional correlation identifier linking related events; auto-generated if
 *     null
 * @param tenantId optional tenant override; falls back to the configured default tenant
 * @param channel optional payment channel (e.g., {@code "MTN"}, {@code "TELECEL"}) used for
 *     channel-aware slot resolution
 * @param amount general-purpose monetary amount (used by single-amount flows)
 * @param grossAmount gross monetary amount before fee deductions
 * @param netAmount net monetary amount after fee deductions
 * @param currency ISO-4217 currency code
 * @param slotOverrides explicit per-request VA id overrides keyed by slot name; never null
 * @param chaos optional chaos injection configuration; null means no chaos applied
 * @param flowFields flow-specific fields keyed by snake_case name; never null
 * @param fees optional typed fee rows for fee-bearing flows (collection, disbursement-completed);
 *     may be null/empty
 */
@RecordBuilder
public record FlowRequest(
    FlowType flowType,
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
    @Nullable List<FeeInput> fees) {}
