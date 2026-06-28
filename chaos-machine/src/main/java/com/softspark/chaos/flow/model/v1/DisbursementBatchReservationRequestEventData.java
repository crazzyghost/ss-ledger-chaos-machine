package com.softspark.chaos.flow.model.v1;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.soabase.recordbuilder.core.RecordBuilder;
import java.math.BigDecimal;
import java.util.Map;
import org.springframework.lang.Nullable;

/**
 * Event data for {@code disbursement.batch.initiated} events with {@code operation =
 * BATCH_RESERVATION_REQUEST}.
 *
 * <p>The first phase of a batch-disbursement fan-out lifecycle: the ledger creates <strong>one</strong>
 * BATCH reservation for {@code total_amount} (= {@code total_principal_amount + total_fees}) against
 * the source (ORGANIZATION) VA, keyed by {@code batch_id}, and declares {@code item_count = N}. The
 * field set is the authoritative {@code ss-ledger-service} contract (see ADR-022). The
 * {@code operation} discriminator distinguishes this from {@link DisbursementBatchItemRequestEventData}
 * on the shared topic.
 *
 * @param operation the operation discriminator (constant {@code BATCH_RESERVATION_REQUEST})
 * @param batchId the batch id (mints the BATCH reservation; idempotency/transaction reference)
 * @param batchCorrelationId the batch correlation id (groups every event of this batch in history)
 * @param merchantId the merchant/organization id (inferred from the source VA)
 * @param sourceVaId the source ORGANIZATION virtual account the reservation is held against
 * @param destinationVaId the platform-float SYSTEM virtual account that receives principal credits
 * @param currency the ISO-4217 currency code
 * @param totalPrincipalAmount the total principal across all N items
 * @param totalFees the total fees across all N items
 * @param totalAmount the total amount reserved ({@code total_principal_amount + total_fees})
 * @param itemCount the number of items N in the batch
 * @param disbursementSubtype {@code DOMESTIC} or {@code CROSS_BORDER}
 * @param callbackUrl optional callback URL (nullable)
 * @param merchantBatchRef the merchant batch reference (ULID/{@code BATCH-REF-…} shaped)
 * @param correlationId the metadata correlation id (the ledger transaction reference)
 * @param requestedAt ISO-8601 timestamp the batch was requested
 * @param authorisedPrincipal the authorising principal ({@code {user_id, key_fingerprint}})
 */
@RecordBuilder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record DisbursementBatchReservationRequestEventData(
    String operation,
    String batchId,
    String batchCorrelationId,
    String merchantId,
    String sourceVaId,
    String destinationVaId,
    String currency,
    BigDecimal totalPrincipalAmount,
    BigDecimal totalFees,
    BigDecimal totalAmount,
    int itemCount,
    String disbursementSubtype,
    @Nullable String callbackUrl,
    String merchantBatchRef,
    String correlationId,
    String requestedAt,
    Map<String, Object> authorisedPrincipal) {}
