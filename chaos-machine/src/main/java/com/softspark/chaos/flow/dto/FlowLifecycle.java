package com.softspark.chaos.flow.dto;

import com.softspark.chaos.flow.model.FlowType;
import io.soabase.recordbuilder.core.RecordBuilder;
import java.util.List;

/**
 * Groups the phases of a multi-step transaction lifecycle (Settlement, Disbursement) and declares the
 * field carry-over from the {@code initiated} phase into its {@code completed}/{@code failed} phases.
 *
 * <p>Attached (non-null) to the {@code initiated} {@link FlowCatalogEntry}; {@code null} on
 * single-shot entries (Collection, the Phase 011 flows). The {@code label} is what the Single Flow
 * Run radio shows for the lifecycle ("Settlement", "Disbursement"). The two-step wizard and the
 * RANDOM runner both read {@code carryOver} to seed the secondary form from the initiated values.
 *
 * @param label the radio/display label for the lifecycle
 * @param initiated the initiated phase flow type
 * @param completed the success phase flow type
 * @param failed the failure phase flow type
 * @param carryOver the initiated-to-secondary field copies (applied where the target descriptor
 *     exists)
 */
@RecordBuilder
public record FlowLifecycle(
    String label,
    FlowType initiated,
    FlowType completed,
    FlowType failed,
    List<CarryOver> carryOver) {}
