package com.softspark.chaos.flow.model.v1;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.soabase.recordbuilder.core.RecordBuilder;
import java.math.BigDecimal;
import org.springframework.lang.Nullable;

/**
 * Event data for {@code disbursement.initiated} events.
 *
 * <p>The first phase of a disbursement lifecycle: the ledger creates a balance reservation keyed by
 * {@code transaction_id} on the merchant's own {@code virtual_account_id}. There is intentionally
 * <strong>no</strong> {@code reservation_id} here — the ledger mints it (see
 * {@code disbursement.completed}/{@code .failed}). The field set is the authoritative
 * {@code ss-ledger-service} contract.
 *
 * @param transactionId the lifecycle/transaction id (carried into completed/failed; idempotency key)
 * @param merchantId the merchant/organization id
 * @param virtualAccountId the merchant's own virtual account (the reservation account)
 * @param merchantRefId the merchant reference id (ULID-shaped)
 * @param narration optional free-text narration (nullable)
 * @param principalAmount the principal amount to disburse
 * @param feeAmount the disbursement fee amount
 * @param currency the ISO-4217 currency code
 * @param disbursementSubtype {@code DOMESTIC} or {@code CROSS_BORDER}
 * @param creditProviderId the credit provider id
 * @param creditAccountId the recipient credit account id
 * @param sourceCountry the ISO source country code
 * @param destinationCountry the ISO destination country code
 * @param corridor the {@code "{source}-{destination}"} corridor (required for CROSS_BORDER)
 * @param fxQuoteReference optional FX quote reference (nullable)
 * @param correlationId the correlation id (the ledger's transaction reference when present)
 * @param requestedAt ISO-8601 timestamp the disbursement was requested
 * @param authorisedPrincipal the authorising principal ({@code {user_id, key_fingerprint}})
 */
@RecordBuilder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record DisbursementInitiatedEventData(
    String transactionId,
    String merchantId,
    String virtualAccountId,
    String merchantRefId,
    @Nullable String narration,
    BigDecimal principalAmount,
    BigDecimal feeAmount,
    String currency,
    String disbursementSubtype,
    String creditProviderId,
    String creditAccountId,
    String sourceCountry,
    String destinationCountry,
    String corridor,
    @Nullable String fxQuoteReference,
    String correlationId,
    String requestedAt,
    AuthorisedPrincipal authorisedPrincipal) {

  /**
   * The authorising principal nested object. The ledger models this as a free-form map with the
   * documented keys {@code user_id} and {@code key_fingerprint}; this typed record assembles exactly
   * those two keys.
   *
   * @param userId the authorising user id
   * @param keyFingerprint the authorising key fingerprint
   */
  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public record AuthorisedPrincipal(String userId, String keyFingerprint) {}
}
