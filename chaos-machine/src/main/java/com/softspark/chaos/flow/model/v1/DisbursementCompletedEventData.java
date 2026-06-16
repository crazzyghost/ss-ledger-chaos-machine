package com.softspark.chaos.flow.model.v1;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.soabase.recordbuilder.core.RecordBuilder;
import java.math.BigDecimal;
import java.util.List;

/**
 * Event data for disbursement.completed events.
 *
 * <p>Published when a disbursement (outbound payment to a recipient) successfully completes. The
 * merchant virtual account is debited and the platform float account is credited.
 *
 * @param disbursementRequestId the disbursement request identifier
 * @param organizationId the organization that initiated the disbursement
 * @param sourceVaId the merchant virtual account (debited)
 * @param destinationVaId the system PLATFORM_FLOAT virtual account (credited)
 * @param grossAmount the total disbursement amount before fee deductions
 * @param netAmount the net amount after fee deductions
 * @param currency the ISO-4217 currency code
 * @param recipientAccountNumber the recipient's bank account number
 * @param recipientBank the recipient's bank code or name
 * @param merchantReference the merchant-provided payment reference
 * @param providerDisbursementId the payment provider's disbursement identifier
 * @param fees the fee entries deducted from the gross amount
 * @param approvedBy the user or system that approved the disbursement
 * @param completedAt ISO-8601 timestamp of completion
 */
@RecordBuilder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record DisbursementCompletedEventData(
    String disbursementRequestId,
    String organizationId,
    String sourceVaId,
    String destinationVaId,
    BigDecimal grossAmount,
    BigDecimal netAmount,
    String currency,
    String recipientAccountNumber,
    String recipientBank,
    String merchantReference,
    String providerDisbursementId,
    List<FeeEntry> fees,
    String approvedBy,
    String completedAt) {

  /**
   * A single fee deducted during the disbursement.
   *
   * @param feeType the fee type identifier
   * @param amount the fee amount
   * @param destinationVaId the virtual account that receives this fee
   */
  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public record FeeEntry(String feeType, BigDecimal amount, String destinationVaId) {}
}
