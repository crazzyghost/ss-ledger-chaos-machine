package com.softspark.chaos.flow.model.v1;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.soabase.recordbuilder.core.RecordBuilder;
import java.math.BigDecimal;
import java.util.List;

/**
 * Event data for collection.completed events.
 *
 * <p>Published when a collection (inbound payment from a merchant) successfully completes. The
 * platform float account is debited and the merchant virtual account is credited.
 *
 * @param collectionRequestId the collection request identifier
 * @param sourceVaId the system PLATFORM_FLOAT virtual account (debited)
 * @param destinationVaId the merchant virtual account (credited)
 * @param grossAmount the total collected amount before fee deductions
 * @param netAmount the amount credited to the merchant after fee deductions
 * @param currency the ISO-4217 currency code
 * @param merchantReference the merchant-provided payment reference
 * @param providerCollectionId the payment provider's collection identifier
 * @param fees the fee entries deducted from the gross amount
 */
@RecordBuilder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CollectionCompletedEventData(
    String collectionRequestId,
    String sourceVaId,
    String destinationVaId,
    BigDecimal grossAmount,
    BigDecimal netAmount,
    String currency,
    String merchantReference,
    String providerCollectionId,
    List<FeeEntry> fees) {

  /**
   * A single fee deducted during the collection.
   *
   * @param feeType the fee type identifier
   * @param amount the fee amount
   * @param destinationVaId the virtual account that receives this fee
   */
  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public record FeeEntry(String feeType, BigDecimal amount, String destinationVaId) {}
}
