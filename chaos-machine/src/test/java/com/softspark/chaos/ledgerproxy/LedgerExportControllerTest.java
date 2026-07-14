package com.softspark.chaos.ledgerproxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.softspark.chaos.advice.GlobalExceptionHandler;
import com.softspark.chaos.config.SecurityConfiguration;
import com.softspark.chaos.exception.BadRequestException;
import com.softspark.chaos.exception.ConflictException;
import com.softspark.chaos.exception.ForbiddenException;
import com.softspark.chaos.exception.NotFoundException;
import com.softspark.chaos.ledgerproxy.circuitbreaker.CircuitBreakerOpenException;
import com.softspark.chaos.ledgerproxy.dto.LedgerExportResult;
import com.softspark.chaos.ledgerproxy.dto.LedgerPageDto;
import com.softspark.chaos.ledgerproxy.dto.LedgerTransactionExportDto;
import com.softspark.chaos.ledgerproxy.dto.LedgerTransactionExportDtoBuilder;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * WebMvc slice tests for {@link LedgerExportController}.
 *
 * <p>Carries the phase's two non-negotiables: no response ever serializes a presigned URL
 * (ADR-034), and the ledger's status codes reach the operator intact — a 403 is a 403 and a 409 is
 * a 409, not the read proxy's blanket 404 (ADR-035).
 */
@WebMvcTest(LedgerExportController.class)
@Import({GlobalExceptionHandler.class, SecurityConfiguration.class})
@DisplayName("LedgerExportController")
class LedgerExportControllerTest {

  private static final String ACCOUNT_ID = "9c1a8b70-6ab4-4a67-9b1e-9f0f5a2b3c44";
  private static final String EXPORT_ID = "1f3c1b6e-6a2e-4a4d-93f8-6f6c1f5a1c11";
  private static final String BASE =
      "/api/v0/ledger/accounts/" + ACCOUNT_ID + "/transaction-exports";
  private static final String PRESIGNED =
      "https://s3.test/bucket/statement.pdf?X-Amz-Signature=abc123";

  @Autowired private MockMvc mockMvc;
  @MockitoBean private LedgerClient ledgerClient;
  @MockitoBean private ArtifactFetcher artifactFetcher;

  private static LedgerTransactionExportDto pendingExport() {
    return new LedgerTransactionExportDto(
        UUID.fromString(EXPORT_ID),
        UUID.fromString(ACCOUNT_ID),
        "PENDING",
        "CSV",
        "MONTHLY",
        LocalDateTime.parse("2026-06-01T00:00:00"),
        LocalDateTime.parse("2026-07-01T00:00:00"),
        null,
        null,
        null,
        null,
        LocalDateTime.parse("2026-07-13T10:00:00"),
        null,
        null,
        null,
        LocalDateTime.parse("2026-07-13T10:00:00"));
  }

  /** A completed export as the ledger returns it — presigned URL and all. */
  private static LedgerTransactionExportDto completedExport() {
    return LedgerTransactionExportDtoBuilder.builder(pendingExport())
        .status("COMPLETED")
        .downloadUrl(URI.create(PRESIGNED))
        .downloadUrlExpiresAt(LocalDateTime.parse("2026-07-13T10:15:30"))
        .completedAt(LocalDateTime.parse("2026-07-13T10:00:12"))
        .build();
  }

  @Nested
  @DisplayName("PUT " + BASE)
  class CreateExport {

    @Test
    @WithMockUser
    @DisplayName("returns 201 with the new export when the ledger created one")
    void returns201WhenCreated() throws Exception {
      when(ledgerClient.createExport(any(), eq(ACCOUNT_ID), eq("csv"), eq("monthly"), any(), any()))
          .thenReturn(new LedgerExportResult(true, pendingExport()));

      mockMvc
          .perform(
              put(BASE)
                  .param("format", "csv")
                  .param("rangeType", "monthly")
                  .param("from", "2026-06-01T00:00:00Z")
                  .accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.id").value(EXPORT_ID))
          .andExpect(jsonPath("$.status").value("PENDING"))
          .andExpect(jsonPath("$.downloadable").value(false));
    }

    @Test
    @WithMockUser
    @DisplayName("returns 200 with the same export id when it joined the active duplicate")
    void returns200WhenJoinedDuplicate() throws Exception {
      when(ledgerClient.createExport(any(), any(), any(), any(), any(), any()))
          .thenReturn(new LedgerExportResult(false, pendingExport()));

      mockMvc
          .perform(
              put(BASE)
                  .param("format", "csv")
                  .param("rangeType", "monthly")
                  .param("from", "2026-06-01T00:00:00Z")
                  .accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.id").value(EXPORT_ID));
    }

    @Test
    @WithMockUser
    @DisplayName("forwards from/to as instants and format/rangeType as opaque strings")
    void forwardsParamsVerbatim() throws Exception {
      when(ledgerClient.createExport(any(), any(), any(), any(), any(), any()))
          .thenReturn(new LedgerExportResult(true, pendingExport()));

      mockMvc
          .perform(
              put(BASE)
                  .param("format", "pdf")
                  .param("rangeType", "custom")
                  .param("from", "2026-06-01T00:00:00Z")
                  .param("to", "2026-07-01T00:00:00Z")
                  .accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isCreated());

      verify(ledgerClient)
          .createExport(
              anyString(),
              eq(ACCOUNT_ID),
              eq("pdf"),
              eq("custom"),
              eq(Instant.parse("2026-06-01T00:00:00Z")),
              eq(Instant.parse("2026-07-01T00:00:00Z")));
    }

    @Test
    @WithMockUser
    @DisplayName("the ledger's 400 arrives as a 400, with its field message")
    void ledger400ArrivesAs400() throws Exception {
      when(ledgerClient.createExport(any(), any(), any(), any(), any(), any()))
          .thenThrow(
              new BadRequestException(
                  "the resolved export window exceeds the maximum of 366 days"));

      mockMvc
          .perform(
              put(BASE)
                  .param("format", "csv")
                  .param("rangeType", "custom")
                  .param("from", "2020-01-01T00:00:00Z")
                  .accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isBadRequest())
          .andExpect(
              jsonPath("$.message")
                  .value("the resolved export window exceeds the maximum of 366 days"));
    }

    @Test
    @WithMockUser
    @DisplayName("the ledger's 403 arrives as a 403, not a 404")
    void ledger403ArrivesAs403() throws Exception {
      when(ledgerClient.createExport(any(), any(), any(), any(), any(), any()))
          .thenThrow(new ForbiddenException("requires ledger_account_transactions:export::allow"));

      mockMvc
          .perform(
              put(BASE)
                  .param("format", "csv")
                  .param("rangeType", "monthly")
                  .param("from", "2026-06-01T00:00:00Z")
                  .accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isForbidden())
          .andExpect(
              jsonPath("$.message").value("requires ledger_account_transactions:export::allow"));
    }

    @Test
    @WithMockUser
    @DisplayName("a missing required param is a 400 from the chaos machine itself")
    void missingRequiredParamIs400() throws Exception {
      mockMvc
          .perform(put(BASE).param("format", "csv").accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    @DisplayName("an open circuit breaker yields the standard unavailable response")
    void circuitBreakerOpen() throws Exception {
      when(ledgerClient.createExport(any(), any(), any(), any(), any(), any()))
          .thenThrow(new CircuitBreakerOpenException("OPEN"));

      mockMvc
          .perform(
              put(BASE)
                  .param("format", "csv")
                  .param("rangeType", "monthly")
                  .param("from", "2026-06-01T00:00:00Z")
                  .accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isInternalServerError())
          .andExpect(jsonPath("$.message").value("Ledger service temporarily unavailable"));
    }

    @Test
    @DisplayName("an unauthenticated request is a 401 — these routes are not permit-all")
    void unauthenticatedIs401() throws Exception {
      mockMvc
          .perform(
              put(BASE)
                  .param("format", "csv")
                  .param("rangeType", "monthly")
                  .param("from", "2026-06-01T00:00:00Z")
                  .accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isUnauthorized());
    }
  }

  @Nested
  @DisplayName("GET " + BASE + "/{exportId}")
  class GetExport {

    @Test
    @WithMockUser
    @DisplayName("downloadable is true for a COMPLETED export — and the presigned URL is gone")
    void completedIsDownloadableAndCarriesNoUrl() throws Exception {
      when(ledgerClient.getExport(anyString(), eq(ACCOUNT_ID), eq(EXPORT_ID)))
          .thenReturn(completedExport());

      var body =
          mockMvc
              .perform(get(BASE + "/" + EXPORT_ID).accept(MediaType.APPLICATION_JSON))
              .andExpect(status().isOk())
              .andExpect(jsonPath("$.status").value("COMPLETED"))
              .andExpect(jsonPath("$.downloadable").value(true))
              .andExpect(jsonPath("$.downloadUrl").doesNotExist())
              .andExpect(jsonPath("$.downloadUrlExpiresAt").doesNotExist())
              .andReturn()
              .getResponse()
              .getContentAsString();

      assertThat(body)
          .doesNotContain("downloadUrl", "downloadUrlExpiresAt", PRESIGNED, "X-Amz-Signature");
    }

    @Test
    @WithMockUser
    @DisplayName("downloadable is false while the export is still PENDING")
    void pendingIsNotDownloadable() throws Exception {
      when(ledgerClient.getExport(anyString(), anyString(), anyString()))
          .thenReturn(pendingExport());

      mockMvc
          .perform(get(BASE + "/" + EXPORT_ID).accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.downloadable").value(false))
          .andExpect(jsonPath("$.errorCode").doesNotExist());
    }

    @Test
    @WithMockUser
    @DisplayName("errorCode is present only on a FAILED export")
    void failedCarriesErrorCode() throws Exception {
      when(ledgerClient.getExport(anyString(), anyString(), anyString()))
          .thenReturn(
              LedgerTransactionExportDtoBuilder.builder(pendingExport())
                  .status("FAILED")
                  .errorCode("RENDER_FAILED")
                  .erroredAt(LocalDateTime.parse("2026-07-13T10:00:05"))
                  .build());

      mockMvc
          .perform(get(BASE + "/" + EXPORT_ID).accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("FAILED"))
          .andExpect(jsonPath("$.errorCode").value("RENDER_FAILED"))
          .andExpect(jsonPath("$.downloadable").value(false));
    }

    @Test
    @WithMockUser
    @DisplayName("the ledger's 404 arrives as a 404")
    void ledger404ArrivesAs404() throws Exception {
      when(ledgerClient.getExport(anyString(), anyString(), anyString()))
          .thenThrow(new NotFoundException("export not found"));

      mockMvc
          .perform(get(BASE + "/" + EXPORT_ID).accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.message").value("export not found"));
    }
  }

  @Nested
  @DisplayName("GET " + BASE)
  class ListExports {

    @Test
    @WithMockUser
    @DisplayName("returns a PageResponse and forwards the status/format filters and paging")
    void returnsPageAndForwardsFilters() throws Exception {
      when(ledgerClient.listExports(
              anyString(), eq(ACCOUNT_ID), eq("PENDING"), eq("CSV"), eq(2), eq(50)))
          .thenReturn(new LedgerPageDto<>(List.of(pendingExport()), 3, 101L, 2, 50));

      mockMvc
          .perform(
              get(BASE)
                  .param("status", "PENDING")
                  .param("format", "CSV")
                  .param("page", "2")
                  .param("pageSize", "50")
                  .accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.items[0].id").value(EXPORT_ID))
          .andExpect(jsonPath("$.items[0].downloadable").value(false))
          .andExpect(jsonPath("$.page").value(2))
          .andExpect(jsonPath("$.perPage").value(50))
          .andExpect(jsonPath("$.total").value(101));
    }

    @Test
    @WithMockUser
    @DisplayName("no listed export leaks a presigned URL either")
    void listLeaksNoUrl() throws Exception {
      when(ledgerClient.listExports(
              anyString(), anyString(), isNull(), isNull(), anyInt(), anyInt()))
          .thenReturn(new LedgerPageDto<>(List.of(completedExport()), 1, 1L, 0, 20));

      var body =
          mockMvc
              .perform(get(BASE).accept(MediaType.APPLICATION_JSON))
              .andExpect(status().isOk())
              .andExpect(jsonPath("$.items[0].downloadable").value(true))
              .andReturn()
              .getResponse()
              .getContentAsString();

      assertThat(body)
          .doesNotContain("downloadUrl", "downloadUrlExpiresAt", PRESIGNED, "X-Amz-Signature");
    }

    @Test
    @WithMockUser
    @DisplayName("forwards pageSize unclamped — the ledger owns the cap and its 400")
    void forwardsPageSizeUnclamped() throws Exception {
      when(ledgerClient.listExports(anyString(), anyString(), isNull(), isNull(), eq(0), eq(500)))
          .thenThrow(new BadRequestException("pageSize must be less than or equal to 100"));

      mockMvc
          .perform(get(BASE).param("pageSize", "500").accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.message").value("pageSize must be less than or equal to 100"));
    }

    @Test
    @WithMockUser
    @DisplayName("an open circuit breaker yields the standard unavailable response")
    void circuitBreakerOpen() throws Exception {
      when(ledgerClient.listExports(anyString(), anyString(), any(), any(), anyInt(), anyInt()))
          .thenThrow(new CircuitBreakerOpenException("OPEN"));

      mockMvc
          .perform(get(BASE).accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isInternalServerError())
          .andExpect(jsonPath("$.message").value("Ledger service temporarily unavailable"));
    }
  }

  @Nested
  @DisplayName("DELETE " + BASE + "/{exportId}")
  class CancelExport {

    @Test
    @WithMockUser
    @DisplayName("returns 200 with the CANCELLED export")
    void cancels() throws Exception {
      when(ledgerClient.cancelExport(anyString(), eq(ACCOUNT_ID), eq(EXPORT_ID)))
          .thenReturn(
              LedgerTransactionExportDtoBuilder.builder(pendingExport())
                  .status("CANCELLED")
                  .cancelledAt(LocalDateTime.parse("2026-07-13T10:00:08"))
                  .build());

      mockMvc
          .perform(delete(BASE + "/" + EXPORT_ID).accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("CANCELLED"))
          .andExpect(jsonPath("$.downloadable").value(false));
    }

    @Test
    @WithMockUser
    @DisplayName("cancelling a terminal export is a 409 with the ledger's message, not a 404")
    void terminalCancelIs409() throws Exception {
      when(ledgerClient.cancelExport(anyString(), anyString(), anyString()))
          .thenThrow(new ConflictException("export is already COMPLETED"));

      mockMvc
          .perform(delete(BASE + "/" + EXPORT_ID).accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isConflict())
          .andExpect(jsonPath("$.message").value("export is already COMPLETED"));
    }

    @Test
    @DisplayName("an unauthenticated cancel is a 401")
    void unauthenticatedIs401() throws Exception {
      mockMvc
          .perform(delete(BASE + "/" + EXPORT_ID).accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isUnauthorized());
    }
  }
}
