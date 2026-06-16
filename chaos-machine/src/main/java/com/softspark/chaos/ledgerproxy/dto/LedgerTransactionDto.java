package com.softspark.chaos.ledgerproxy.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.soabase.recordbuilder.core.RecordBuilder;
import jakarta.annotation.Nullable;
import java.math.BigDecimal;

/**
 * DTO mirroring the ledger's transaction/journal-entry response shape.
 *
 * @param transactionId the ledger-assigned transaction UUID
 * @param eventId the originating event UUID
 * @param eventType the event type string (e.g. "collection.completed")
 * @param sourceVaId the source virtual account id
 * @param destinationVaId the destination virtual account id
 * @param amount the transaction amount
 * @param currency the ISO-4217 currency code
 * @param status the transaction status
 * @param correlationId the correlation id linking related events
 * @param createdAt ISO-8601 creation timestamp
 */
@RecordBuilder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public record LedgerTransactionDto(
    String transactionId,
    @Nullable String eventId,
    @Nullable String eventType,
    @Nullable String sourceVaId,
    @Nullable String destinationVaId,
    @Nullable BigDecimal amount,
    @Nullable String currency,
    @Nullable String status,
    @Nullable String correlationId,
    @Nullable String createdAt) {}
