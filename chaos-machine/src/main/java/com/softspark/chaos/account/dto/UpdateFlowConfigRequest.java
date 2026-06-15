package com.softspark.chaos.account.dto;

import io.soabase.recordbuilder.core.RecordBuilder;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Request record for updating flow slot configuration.
 *
 * @param slots the list of slot updates
 */
@RecordBuilder
public record UpdateFlowConfigRequest(
        @NotEmpty(message = "Slots list cannot be empty")
        List<@Valid SlotUpdate> slots
) {

    /**
     * Update for a single slot within a flow.
     *
     * @param slotName     the slot name to update
     * @param accountRole  the account role to assign (optional if explicitVaId is provided)
     * @param explicitVaId the explicit virtual account ID (optional if accountRole is provided)
     */
    @RecordBuilder
    public record SlotUpdate(
            @NotNull(message = "Slot name is required")
            String slotName,
            String accountRole,
            String explicitVaId
    ) {
    }
}
