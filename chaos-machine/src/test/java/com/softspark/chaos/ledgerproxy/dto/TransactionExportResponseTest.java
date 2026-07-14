package com.softspark.chaos.ledgerproxy.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Mapping and leak-proofing tests for {@link TransactionExportResponse}.
 *
 * <p>The load-bearing assertion is {@link #hasNoDownloadUrlComponentAtAll()}: the API record must
 * have <em>no component</em> capable of carrying the ledger's presigned URL (ADR-034). A test that
 * merely checked "the mapper doesn't copy it" would pass right up until someone adds the field back.
 */
@DisplayName("TransactionExportResponse")
class TransactionExportResponseTest {

  private static final UUID EXPORT_ID = UUID.fromString("1f3c1b6e-6a2e-4a4d-93f8-6f6c1f5a1c11");
  private static final UUID ACCOUNT_ID = UUID.fromString("9c1a8b70-6ab4-4a67-9b1e-9f0f5a2b3c44");
  private static final UUID INITIATED_BY = UUID.fromString("5b0e4d2a-9f1c-4a8e-8a5f-2c3d4e5f6a7b");

  private static LedgerTransactionExportDto completed() {
    return new LedgerTransactionExportDto(
        EXPORT_ID,
        ACCOUNT_ID,
        "COMPLETED",
        "PDF",
        "MONTHLY",
        LocalDateTime.parse("2026-06-01T00:00:00"),
        LocalDateTime.parse("2026-07-01T00:00:00"),
        URI.create("https://s3.test/bucket/statement.pdf?X-Amz-Signature=abc123"),
        LocalDateTime.parse("2026-07-13T10:15:30"),
        null,
        INITIATED_BY,
        LocalDateTime.parse("2026-07-13T10:00:00"),
        LocalDateTime.parse("2026-07-13T10:00:12"),
        null,
        null,
        LocalDateTime.parse("2026-07-13T10:00:00"));
  }

  private static LedgerTransactionExportDto withStatus(String status) {
    return LedgerTransactionExportDtoBuilder.builder(completed())
        .status(status)
        .downloadUrl(null)
        .downloadUrlExpiresAt(null)
        .completedAt(null)
        .build();
  }

  @Test
  @DisplayName("maps every ledger field across, dropping the presigned URL and its expiry")
  void mapsEveryField() {
    var response = TransactionExportResponse.from(completed());

    assertThat(response.id()).isEqualTo(EXPORT_ID.toString());
    assertThat(response.accountId()).isEqualTo(ACCOUNT_ID.toString());
    assertThat(response.status()).isEqualTo("COMPLETED");
    assertThat(response.format()).isEqualTo("PDF");
    assertThat(response.rangeType()).isEqualTo("MONTHLY");
    assertThat(response.rangeFrom()).isEqualTo(LocalDateTime.parse("2026-06-01T00:00:00"));
    assertThat(response.rangeTo()).isEqualTo(LocalDateTime.parse("2026-07-01T00:00:00"));
    assertThat(response.errorCode()).isNull();
    assertThat(response.initiatedBy()).isEqualTo(INITIATED_BY.toString());
    assertThat(response.initiatedAt()).isEqualTo(LocalDateTime.parse("2026-07-13T10:00:00"));
    assertThat(response.completedAt()).isEqualTo(LocalDateTime.parse("2026-07-13T10:00:12"));
    assertThat(response.cancelledAt()).isNull();
    assertThat(response.erroredAt()).isNull();
    assertThat(response.createdAt()).isEqualTo(LocalDateTime.parse("2026-07-13T10:00:00"));
  }

  @Test
  @DisplayName("downloadable is true only for COMPLETED")
  void downloadableOnlyWhenCompleted() {
    assertThat(TransactionExportResponse.from(completed()).downloadable()).isTrue();

    for (var status : new String[] {"PENDING", "IN_PROGRESS", "FAILED", "CANCELLED"}) {
      assertThat(TransactionExportResponse.from(withStatus(status)).downloadable())
          .as("downloadable for %s", status)
          .isFalse();
    }
  }

  @Test
  @DisplayName("errorCode survives on a FAILED export")
  void carriesErrorCodeOnFailure() {
    var failed =
        LedgerTransactionExportDtoBuilder.builder(withStatus("FAILED"))
            .errorCode("RENDER_FAILED")
            .erroredAt(LocalDateTime.parse("2026-07-13T10:00:05"))
            .build();

    var response = TransactionExportResponse.from(failed);

    assertThat(response.status()).isEqualTo("FAILED");
    assertThat(response.errorCode()).isEqualTo("RENDER_FAILED");
    assertThat(response.downloadable()).isFalse();
  }

  @Test
  @DisplayName("has no downloadUrl component at all — the omission is structural, not a discipline")
  void hasNoDownloadUrlComponentAtAll() {
    var componentNames =
        Arrays.stream(TransactionExportResponse.class.getRecordComponents())
            .map(java.lang.reflect.RecordComponent::getName)
            .toList();

    assertThat(componentNames).doesNotContain("downloadUrl", "downloadUrlExpiresAt");
    assertThat(TransactionExportResponse.class.getRecordComponents())
        .noneMatch(c -> c.getType().equals(URI.class));
  }

  @Test
  @DisplayName("serialized JSON of a COMPLETED export mentions no URL, and no signature")
  void serializesWithoutTheUrl() throws Exception {
    var mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    var json = mapper.writeValueAsString(TransactionExportResponse.from(completed()));

    assertThat(json).doesNotContain("downloadUrl", "downloadUrlExpiresAt", "X-Amz-Signature", "s3");
    assertThat(json).contains("\"downloadable\":true");
  }
}
