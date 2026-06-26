package com.softspark.chaos.ledgerproxy.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import org.springframework.lang.Nullable;

/**
 * DTO mirroring the ledger's reservation response shape.
 *
 * <p>The ledger serializes reservations in {@code camelCase} (the endpoint returns a raw Spring
 * {@code Page}, no {@code @JsonNaming} on the element), so this DTO uses the default (camelCase)
 * naming and is passed through to the admin UI unchanged. {@code id} is the reservation id the chaos
 * machine needs for a disbursement's second event; {@code transactionRef} equals the
 * driver-controlled {@code transaction_id}.
 *
 * @param id the reservation id (the value sourced into {@code reservation_id})
 * @param accountId the ledger account the reservation is on
 * @param transactionRef the transaction reference (equals the disbursement {@code transaction_id})
 * @param type the reservation type ({@code SINGLE}/{@code BATCH})
 * @param status the reservation status ({@code ACTIVE}/{@code CAPTURED}/{@code RELEASED}/…)
 * @param amount the reserved amount
 * @param amountCaptured the captured amount
 * @param amountReleased the released amount
 * @param disbursementBatchId the disbursement batch id (nullable)
 * @param expiresAt the reservation expiry timestamp (nullable)
 * @param createdAt the reservation creation timestamp (nullable)
 * @param resolvedAt the reservation resolution timestamp (nullable)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ReservationResponse(
    String id,
    String accountId,
    String transactionRef,
    String type,
    String status,
    BigDecimal amount,
    BigDecimal amountCaptured,
    BigDecimal amountReleased,
    @Nullable String disbursementBatchId,
    @Nullable String expiresAt,
    @Nullable String createdAt,
    @Nullable String resolvedAt) {}
