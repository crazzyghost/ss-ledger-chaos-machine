package com.softspark.chaos.flow.model.v1;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.soabase.recordbuilder.core.RecordBuilder;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.lang.Nullable;

/**
 * Event data for {@code collection.completed} events.
 *
 * <p>Published when a collection (inbound payment from a merchant) successfully completes. The
 * platform float account is debited and the merchant virtual account is credited. The field set is
 * the authoritative {@code ss-ledger-service} contract (verified source + the ledger publish
 * samples); the ledger uses {@code transaction_id} as its idempotency key.
 *
 * @param transactionId the lifecycle/transaction id (the ledger's idempotency key)
 * @param sourceVaId the system PLATFORM_FLOAT virtual account (debited)
 * @param destinationVaId the merchant/organization virtual account (credited)
 * @param providerId the payment provider identifier
 * @param providerReferenceId the payment provider's reference id
 * @param grossAmount the total collected amount ({@code net + Σ fee.amount})
 * @param netAmount the amount credited to the merchant after fee deductions
 * @param currency the ISO-4217 currency code
 * @param fees the fee lines deducted from the gross amount (never null; may be empty)
 * @param commissionSplitId the optional commission-split id (nullable)
 * @param completedAt ISO-8601 timestamp of completion
 * @param merchantRefId the merchant reference id (ULID-shaped)
 */
@RecordBuilder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CollectionCompletedEventData(
    String transactionId,
    String sourceVaId,
    String destinationVaId,
    String providerId,
    String providerReferenceId,
    BigDecimal grossAmount,
    BigDecimal netAmount,
    String currency,
    List<TransactionFeeLine> fees,
    @Nullable String commissionSplitId,
    String completedAt,
    String merchantRefId) {}
