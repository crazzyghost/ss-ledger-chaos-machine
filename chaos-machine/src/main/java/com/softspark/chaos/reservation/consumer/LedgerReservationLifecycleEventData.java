package com.softspark.chaos.reservation.consumer;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Mirror of the ledger's {@code ReservationLifecycleEventData} (snake_case, no JSON type headers).
 * <strong>One record backs both topics</strong> ({@code ledger.reservation.created} and {@code
 * .released}); {@code event_type} + {@code status} disambiguate created from transition (ADR-028).
 *
 * <p>{@code reservationType} and {@code status} are kept as {@code String} (not enums) so an
 * unrecognised value tolerates contract drift rather than failing deserialization.
 *
 * <p>{@code transactionId} is the publisher's inbound {@code transactionRef} (= the chaos request
 * id: disbursement {@code transaction_id}, settlement {@code settlement_request_id}, batch {@code
 * batch_id}) — so reservations are precisely correlatable to a chaos publish.
 *
 * @param reservationId the reservation id (natural key; one row per reservation)
 * @param accountId the account id (= chaos {@code va_id})
 * @param transactionId the inbound transactionRef = chaos request id (correlation key)
 * @param reservationType SINGLE | BATCH
 * @param amount the total held
 * @param status ACTIVE | PARTIALLY_RESOLVED | CAPTURED | RELEASED | EXPIRED
 * @param disbursementBatchId the batch id (nullable; set for BATCH)
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record LedgerReservationLifecycleEventData(
    UUID reservationId,
    UUID accountId,
    String transactionId,
    String reservationType,
    BigDecimal amount,
    String status,
    String disbursementBatchId) {}
