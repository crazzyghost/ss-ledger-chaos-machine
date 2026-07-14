package com.softspark.chaos.ledgerproxy.dto;

import io.soabase.recordbuilder.core.RecordBuilder;
import jakarta.annotation.Nullable;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * The chaos API's view of a statement export — every field the ledger returns <em>except</em> the
 * presigned {@code downloadUrl} and its expiry.
 *
 * <p>The omission is structural, not a discipline (ADR-034): this record has no URL component, so a
 * later edit cannot leak the presigned S3 capability to the browser by forgetting to strip it. The
 * UI reaches the artifact through the chaos machine's own download route instead, and learns whether
 * there is one from {@link #downloadable()} rather than from the presence of a URL.
 *
 * @param id the export id
 * @param accountId the exported account
 * @param status lifecycle status ({@code PENDING}/{@code IN_PROGRESS}/{@code COMPLETED}/{@code
 *     FAILED}/{@code CANCELLED})
 * @param format the artifact format ({@code CSV}/{@code PDF})
 * @param rangeType the window kind ({@code DAILY}…{@code CUSTOM})
 * @param rangeFrom resolved window start (UTC, inclusive)
 * @param rangeTo resolved window end (UTC, exclusive)
 * @param downloadable whether an artifact exists to download — derived, true only for {@code
 *     COMPLETED}
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
public record TransactionExportResponse(
    String id,
    String accountId,
    String status,
    String format,
    String rangeType,
    LocalDateTime rangeFrom,
    LocalDateTime rangeTo,
    boolean downloadable,
    @Nullable String errorCode,
    @Nullable String initiatedBy,
    LocalDateTime initiatedAt,
    @Nullable LocalDateTime completedAt,
    @Nullable LocalDateTime cancelledAt,
    @Nullable LocalDateTime erroredAt,
    LocalDateTime createdAt) {

  private static final String COMPLETED = "COMPLETED";

  /**
   * Maps the ledger's export DTO onto the API-facing shape, dropping the presigned URL and its
   * expiry and deriving {@code downloadable} from the status.
   *
   * @param dto the ledger's export representation
   * @return the API-facing export, carrying no download URL
   */
  public static TransactionExportResponse from(LedgerTransactionExportDto dto) {
    return new TransactionExportResponse(
        Objects.toString(dto.id(), null),
        Objects.toString(dto.accountId(), null),
        dto.status(),
        dto.format(),
        dto.rangeType(),
        dto.rangeFrom(),
        dto.rangeTo(),
        COMPLETED.equals(dto.status()),
        dto.errorCode(),
        Objects.toString(dto.initiatedBy(), null),
        dto.initiatedAt(),
        dto.completedAt(),
        dto.cancelledAt(),
        dto.erroredAt(),
        dto.createdAt());
  }
}
