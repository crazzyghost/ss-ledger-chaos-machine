package com.softspark.chaos.ledgerproxy.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Deserialization contract tests for {@link LedgerTransactionExportDto} against captured {@code
 * ss-ledger-service} export payloads.
 *
 * <p>Pins the <strong>camelCase</strong> casing of the ledger's REST contract. Only the ledger's
 * Kafka payloads are snake_case; a {@code @JsonNaming(SnakeCaseStrategy)} added here would silently
 * null {@code accountId}, {@code rangeFrom} and {@code downloadUrl} — exactly the bug
 * {@link LedgerBalanceDto} carries in its javadoc. This test makes that failure loud.
 */
@DisplayName("LedgerTransactionExportDto")
class LedgerTransactionExportDtoTest {

  private ObjectMapper mapper;

  @BeforeEach
  void setUp() {
    mapper = new ObjectMapper();
    mapper.registerModule(new JavaTimeModule());
  }

  @Test
  @DisplayName("deserializes a COMPLETED export, presigned downloadUrl and all")
  void deserializesCompletedExport() throws Exception {
    var json =
        """
        {
          "id": "1f3c1b6e-6a2e-4a4d-93f8-6f6c1f5a1c11",
          "accountId": "9c1a8b70-6ab4-4a67-9b1e-9f0f5a2b3c44",
          "status": "COMPLETED",
          "format": "PDF",
          "rangeType": "MONTHLY",
          "rangeFrom": "2026-06-01T00:00:00",
          "rangeTo": "2026-07-01T00:00:00",
          "downloadUrl": "https://s3.test/bucket/statement.pdf?X-Amz-Signature=abc123",
          "downloadUrlExpiresAt": "2026-07-13T10:15:30",
          "errorCode": null,
          "initiatedBy": "5b0e4d2a-9f1c-4a8e-8a5f-2c3d4e5f6a7b",
          "initiatedAt": "2026-07-13T10:00:00",
          "completedAt": "2026-07-13T10:00:12",
          "cancelledAt": null,
          "erroredAt": null,
          "createdAt": "2026-07-13T10:00:00"
        }
        """;

    var dto = mapper.readValue(json, LedgerTransactionExportDto.class);

    assertThat(dto.id()).isEqualTo(UUID.fromString("1f3c1b6e-6a2e-4a4d-93f8-6f6c1f5a1c11"));
    assertThat(dto.accountId()).isEqualTo(UUID.fromString("9c1a8b70-6ab4-4a67-9b1e-9f0f5a2b3c44"));
    assertThat(dto.status()).isEqualTo("COMPLETED");
    assertThat(dto.format()).isEqualTo("PDF");
    assertThat(dto.rangeType()).isEqualTo("MONTHLY");
    assertThat(dto.rangeFrom()).isEqualTo(LocalDateTime.parse("2026-06-01T00:00:00"));
    assertThat(dto.rangeTo()).isEqualTo(LocalDateTime.parse("2026-07-01T00:00:00"));
    assertThat(dto.downloadUrl())
        .isEqualTo(URI.create("https://s3.test/bucket/statement.pdf?X-Amz-Signature=abc123"));
    assertThat(dto.downloadUrlExpiresAt()).isEqualTo(LocalDateTime.parse("2026-07-13T10:15:30"));
    assertThat(dto.errorCode()).isNull();
    assertThat(dto.initiatedBy())
        .isEqualTo(UUID.fromString("5b0e4d2a-9f1c-4a8e-8a5f-2c3d4e5f6a7b"));
    assertThat(dto.initiatedAt()).isEqualTo(LocalDateTime.parse("2026-07-13T10:00:00"));
    assertThat(dto.completedAt()).isEqualTo(LocalDateTime.parse("2026-07-13T10:00:12"));
    assertThat(dto.cancelledAt()).isNull();
    assertThat(dto.erroredAt()).isNull();
    assertThat(dto.createdAt()).isEqualTo(LocalDateTime.parse("2026-07-13T10:00:00"));
  }

  @Test
  @DisplayName("deserializes a PENDING export — no URL, no terminal timestamps")
  void deserializesPendingExport() throws Exception {
    var json =
        """
        {
          "id": "1f3c1b6e-6a2e-4a4d-93f8-6f6c1f5a1c11",
          "accountId": "9c1a8b70-6ab4-4a67-9b1e-9f0f5a2b3c44",
          "status": "PENDING",
          "format": "CSV",
          "rangeType": "CUSTOM",
          "rangeFrom": "2026-06-01T00:00:00",
          "rangeTo": "2026-06-15T00:00:00",
          "downloadUrl": null,
          "downloadUrlExpiresAt": null,
          "errorCode": null,
          "initiatedBy": null,
          "initiatedAt": "2026-07-13T10:00:00",
          "completedAt": null,
          "cancelledAt": null,
          "erroredAt": null,
          "createdAt": "2026-07-13T10:00:00"
        }
        """;

    var dto = mapper.readValue(json, LedgerTransactionExportDto.class);

    assertThat(dto.status()).isEqualTo("PENDING");
    assertThat(dto.format()).isEqualTo("CSV");
    assertThat(dto.downloadUrl()).isNull();
    assertThat(dto.downloadUrlExpiresAt()).isNull();
    assertThat(dto.initiatedBy()).isNull();
    assertThat(dto.completedAt()).isNull();
    assertThat(dto.accountId()).isEqualTo(UUID.fromString("9c1a8b70-6ab4-4a67-9b1e-9f0f5a2b3c44"));
  }

  @Test
  @DisplayName("deserializes a FAILED export's errorCode as a string, not a mirrored enum")
  void deserializesFailedExport() throws Exception {
    var json =
        """
        {
          "id": "1f3c1b6e-6a2e-4a4d-93f8-6f6c1f5a1c11",
          "accountId": "9c1a8b70-6ab4-4a67-9b1e-9f0f5a2b3c44",
          "status": "FAILED",
          "format": "PDF",
          "rangeType": "DAILY",
          "rangeFrom": "2026-06-01T00:00:00",
          "rangeTo": "2026-06-02T00:00:00",
          "errorCode": "A_CODE_THIS_BUILD_HAS_NEVER_HEARD_OF",
          "initiatedAt": "2026-07-13T10:00:00",
          "erroredAt": "2026-07-13T10:00:05",
          "createdAt": "2026-07-13T10:00:00"
        }
        """;

    var dto = mapper.readValue(json, LedgerTransactionExportDto.class);

    assertThat(dto.status()).isEqualTo("FAILED");
    assertThat(dto.errorCode()).isEqualTo("A_CODE_THIS_BUILD_HAS_NEVER_HEARD_OF");
    assertThat(dto.erroredAt()).isEqualTo(LocalDateTime.parse("2026-07-13T10:00:05"));
  }

  @Test
  @DisplayName("absorbs unknown fields the ledger may add later")
  void ignoresUnknownFields() throws Exception {
    var json =
        """
        {
          "id": "1f3c1b6e-6a2e-4a4d-93f8-6f6c1f5a1c11",
          "accountId": "9c1a8b70-6ab4-4a67-9b1e-9f0f5a2b3c44",
          "status": "PENDING",
          "format": "CSV",
          "rangeType": "DAILY",
          "rangeFrom": "2026-06-01T00:00:00",
          "rangeTo": "2026-06-02T00:00:00",
          "initiatedAt": "2026-07-13T10:00:00",
          "createdAt": "2026-07-13T10:00:00",
          "aFieldFromTomorrowsLedger": "surprise"
        }
        """;

    var dto = mapper.readValue(json, LedgerTransactionExportDto.class);

    assertThat(dto.status()).isEqualTo("PENDING");
  }
}
