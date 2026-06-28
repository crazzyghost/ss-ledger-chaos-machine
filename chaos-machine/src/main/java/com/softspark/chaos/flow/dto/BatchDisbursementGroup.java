package com.softspark.chaos.flow.dto;

import com.softspark.chaos.flow.model.FlowType;
import io.soabase.recordbuilder.core.RecordBuilder;
import java.util.List;

/**
 * Groups the four phases of a batch-disbursement <em>fan-out</em> lifecycle (one reservation → N
 * items, each item a request → completed|failed) and declares the carry-over maps the interactive
 * wizard and the automatic runner use to build the item events.
 *
 * <p>The fan-out analogue of {@link FlowLifecycle} (which models a single {@code initiated →
 * completed|failed} transaction). Attached (non-null) to the
 * {@link FlowType#DISBURSEMENT_BATCH_RESERVATION_REQUEST} {@link FlowCatalogEntry}; that entry is the
 * single {@code runnerVisible} "Batch Disbursement" radio choice. The other three phases keep full
 * descriptors but are not standalone radio choices. A {@link FlowCatalogEntry} carries at most one of
 * {@link FlowCatalogEntry#lifecycle()} or {@link FlowCatalogEntry#batchGroup()}.
 *
 * @param label the radio/display label for the batch flow ("Batch Disbursement")
 * @param reservation the reservation phase flow type (mints {@code batch_id})
 * @param itemRequest the per-item request phase flow type (inert at the ledger)
 * @param itemCompleted the per-item success phase flow type (partial capture)
 * @param itemFailed the per-item failure phase flow type (partial release)
 * @param reservationToItem field copies from the reservation form into each item form (batch_id,
 *     batch_correlation_id, merchant_id, reservation_id, source VA → virtual_account_id, currency,
 *     disbursement_subtype, correlation_id)
 * @param itemRequestToTerminal field copies from an item request into its terminal (item_id,
 *     item_sequence, principal_amount, item_fee, virtual_account_id, disbursement_subtype,
 *     merchant_item_ref, provider_id, corridor/destination_country)
 */
@RecordBuilder
public record BatchDisbursementGroup(
    String label,
    FlowType reservation,
    FlowType itemRequest,
    FlowType itemCompleted,
    FlowType itemFailed,
    List<CarryOver> reservationToItem,
    List<CarryOver> itemRequestToTerminal) {}
