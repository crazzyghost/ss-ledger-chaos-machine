package com.softspark.chaos.reservation.dto;

import com.softspark.chaos.reservation.model.Reservation;
import io.soabase.recordbuilder.core.RecordBuilder;
import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.lang.Nullable;

/**
 * Response DTO for a {@code reservation} projection row (ADR-028).
 *
 * <p>Named {@code ReservationStateResponse} (not {@code ReservationResponse}, which already exists in
 * {@code ledgerproxy} for the read-proxy) — this projection is push-fed and event-faithful, distinct
 * from the on-demand read-proxy which exposes richer fields (captured/released amounts, expiry).
 *
 * @param reservationId the reservation id
 * @param accountId the account id (= VA id)
 * @param transactionId the inbound transactionRef = chaos request id
 * @param reservationType SINGLE | BATCH
 * @param disbursementBatchId the batch id (nullable; set for BATCH)
 * @param amount the total held
 * @param currency best-effort currency from the VA registry (nullable)
 * @param status current lifecycle status
 * @param releaseEventCount number of release-topic events applied (best-effort)
 * @param createdAt envelope timestamp of the ACTIVE (created) event (nullable until observed)
 * @param updatedAt envelope timestamp of the latest applied event
 * @param terminalAt when the status first became terminal (nullable)
 * @param payloadJson the latest raw envelope (detail view)
 */
@RecordBuilder
public record ReservationStateResponse(
    String reservationId,
    String accountId,
    String transactionId,
    String reservationType,
    @Nullable String disbursementBatchId,
    BigDecimal amount,
    @Nullable String currency,
    String status,
    int releaseEventCount,
    @Nullable Instant createdAt,
    Instant updatedAt,
    @Nullable Instant terminalAt,
    @Nullable String payloadJson) {

  /**
   * Maps a {@link Reservation} entity to a response DTO.
   *
   * @param entity the entity to map
   * @return the response DTO
   */
  public static ReservationStateResponse from(Reservation entity) {
    return new ReservationStateResponse(
        entity.getReservationId(),
        entity.getAccountId(),
        entity.getTransactionId(),
        entity.getReservationType(),
        entity.getDisbursementBatchId(),
        entity.getAmount(),
        entity.getCurrency(),
        entity.getStatus(),
        entity.getReleaseEventCount(),
        entity.getCreatedAt(),
        entity.getUpdatedAt(),
        entity.getTerminalAt(),
        entity.getPayloadJson());
  }
}
