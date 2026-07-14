package com.softspark.chaos.ledgerproxy.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.soabase.recordbuilder.core.RecordBuilder;
import jakarta.annotation.Nullable;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Internal DTO mirroring the ledger's {@code TransactionExportResponse} verbatim — including the
 * presigned {@code downloadUrl} the chaos API must never expose.
 *
 * <p>This is the shape {@link com.softspark.chaos.ledgerproxy.LedgerClient} deserializes. It is
 * deliberately <em>not</em> the shape any controller returns: {@link TransactionExportResponse} is,
 * and it has no URL component at all, so the presigned capability cannot reach a response body even
 * by accident (ADR-034). The chaos machine resolves the URL server-side, for the download proxy
 * only.
 *
 * <p>The ledger's REST DTOs serialize <strong>camelCase</strong> (only its Kafka event payloads are
 * snake_case) — do not add a {@code @JsonNaming(SnakeCaseStrategy)} here. That mistake silently
 * nulled {@code accountId} on {@link LedgerBalanceDto}; {@code LedgerTransactionExportDtoTest} pins
 * the casing against a captured payload so it fails loudly rather than quietly.
 *
 * <p>{@code status}, {@code format}, {@code rangeType} and {@code errorCode} are carried as
 * <strong>strings</strong>, not mirrored enums (ADR-033): a new ledger format must pass through the
 * proxy, not be rejected by it pending a chaos release.
 *
 * @param id the export id
 * @param accountId the exported account
 * @param status lifecycle status ({@code PENDING}/{@code IN_PROGRESS}/{@code COMPLETED}/{@code
 *     FAILED}/{@code CANCELLED})
 * @param format the requested artifact format ({@code CSV}/{@code PDF})
 * @param rangeType the requested window kind ({@code DAILY}…{@code CUSTOM})
 * @param rangeFrom resolved window start (UTC, inclusive)
 * @param rangeTo resolved window end (UTC, exclusive)
 * @param downloadUrl freshly presigned by the ledger on every read, present only when {@code status}
 *     is {@code COMPLETED}; <strong>server-side use only</strong>
 * @param downloadUrlExpiresAt when {@code downloadUrl} expires; server-side use only
 * @param errorCode why the export failed, present only when {@code status} is {@code FAILED}
 * @param initiatedBy the token subject that created the export; {@code null} when ledger auth is
 *     disabled
 * @param initiatedAt creation instant (UTC)
 * @param completedAt completion instant (UTC), present only once {@code COMPLETED}
 * @param cancelledAt cancellation instant (UTC), present only once {@code CANCELLED}
 * @param erroredAt failure instant (UTC), present only once {@code FAILED}
 * @param createdAt row creation instant (UTC)
 */
@RecordBuilder
@JsonIgnoreProperties(ignoreUnknown = true)
public record LedgerTransactionExportDto(
    UUID id,
    UUID accountId,
    String status,
    String format,
    String rangeType,
    LocalDateTime rangeFrom,
    LocalDateTime rangeTo,
    @Nullable URI downloadUrl,
    @Nullable LocalDateTime downloadUrlExpiresAt,
    @Nullable String errorCode,
    @Nullable UUID initiatedBy,
    LocalDateTime initiatedAt,
    @Nullable LocalDateTime completedAt,
    @Nullable LocalDateTime cancelledAt,
    @Nullable LocalDateTime erroredAt,
    LocalDateTime createdAt) {}
