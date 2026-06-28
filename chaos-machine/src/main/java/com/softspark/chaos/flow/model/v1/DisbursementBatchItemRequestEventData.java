package com.softspark.chaos.flow.model.v1;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.soabase.recordbuilder.core.RecordBuilder;
import java.math.BigDecimal;
import org.springframework.lang.Nullable;

/**
 * Event data for {@code disbursement.batch.initiated} events with {@code operation =
 * BATCH_ITEM_REQUEST}.
 *
 * <p>The per-item request phase. <strong>Inert</strong> at the ledger: it records the event for
 * idempotency but performs no aggregate side-effects; it is published once per item for downstream
 * intent tracking. The field set is the authoritative {@code ss-ledger-service} contract (see
 * ADR-022). The {@code operation} discriminator distinguishes this from
 * {@link DisbursementBatchReservationRequestEventData} on the shared topic.
 *
 * @param operation the operation discriminator (constant {@code BATCH_ITEM_REQUEST})
 * @param batchId the batch id (carried from the reservation)
 * @param batchCorrelationId the batch correlation id (carried from the reservation)
 * @param itemId the per-item id (carried into the item terminal; item idempotency reference)
 * @param itemSequence the 1-based sequence of this item within the batch
 * @param merchantItemRef the merchant item reference (ULID/{@code ITEM-REF-…} shaped)
 * @param merchantId the merchant/organization id (carried)
 * @param virtualAccountId the batch source ORGANIZATION virtual account (carried)
 * @param principalAmount the item's principal slice
 * @param currency the ISO-4217 currency code (carried)
 * @param creditProviderId the credit provider id
 * @param creditAccountId the recipient credit account id
 * @param disbursementSubtype {@code DOMESTIC} or {@code CROSS_BORDER} (carried)
 * @param sourceCountry the ISO source country code (cross-border; nullable)
 * @param destinationCountry the ISO destination country code (cross-border; nullable)
 * @param corridor the {@code "{source}-{destination}"} corridor (cross-border; nullable)
 * @param fxQuoteReference optional FX quote reference (nullable)
 * @param itemFee the item's fee slice
 * @param correlationId the metadata correlation id (carried)
 * @param requestedAt ISO-8601 timestamp the item was requested
 */
@RecordBuilder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record DisbursementBatchItemRequestEventData(
    String operation,
    String batchId,
    String batchCorrelationId,
    String itemId,
    int itemSequence,
    String merchantItemRef,
    String merchantId,
    String virtualAccountId,
    BigDecimal principalAmount,
    String currency,
    String creditProviderId,
    String creditAccountId,
    String disbursementSubtype,
    @Nullable String sourceCountry,
    @Nullable String destinationCountry,
    @Nullable String corridor,
    @Nullable String fxQuoteReference,
    BigDecimal itemFee,
    String correlationId,
    String requestedAt) {}
