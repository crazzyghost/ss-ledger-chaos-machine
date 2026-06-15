package com.softspark.chaos.account.dto;

import com.softspark.chaos.flow.model.FlowType;
import io.soabase.recordbuilder.core.RecordBuilder;

import java.util.List;

/**
 * Response record representing flow slot configuration.
 *
 * @param flowType the transaction flow type
 * @param slots    the slot configurations for this flow
 */
@RecordBuilder
public record FlowConfigResponse(
        FlowType flowType,
        List<SlotConfig> slots
) {

    /**
     * Configuration for a single slot within a flow.
     *
     * @param slotName      the slot name
     * @param accountRole   the account role assigned to this slot (if any)
     * @param explicitVaId  the explicit virtual account ID (if any)
     * @param effectiveVaId the resolved VA ID (role's default or explicit)
     */
    @RecordBuilder
    public record SlotConfig(
            String slotName,
            String accountRole,
            String explicitVaId,
            String effectiveVaId
    ) {
    }
}
