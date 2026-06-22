package com.softspark.chaos.ledgerproxy;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.softspark.chaos.advice.GlobalExceptionHandler;
import com.softspark.chaos.config.SecurityConfiguration;
import com.softspark.chaos.ledgerproxy.circuitbreaker.CircuitBreakerOpenException;
import com.softspark.chaos.ledgerproxy.dto.LedgerAccountDto;
import com.softspark.chaos.ledgerproxy.dto.LedgerCursorPageDto;
import com.softspark.chaos.ledgerproxy.dto.LedgerPageDto;
import com.softspark.chaos.ledgerproxy.dto.LedgerTransactionHistoryDto;
import com.softspark.chaos.ledgerproxy.dto.LedgerTransactionReferenceDto;
import java.math.BigDecimal;
import java.util.List;
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
 * WebMvc slice tests for {@link LedgerReadController}.
 */
@WebMvcTest(LedgerReadController.class)
@Import({GlobalExceptionHandler.class, SecurityConfiguration.class})
@DisplayName("LedgerReadController")
class LedgerReadControllerTest {

  @Autowired private MockMvc mockMvc;
  @MockitoBean private LedgerClient ledgerClient;

  private LedgerAccountDto sampleAccount() {
    return new LedgerAccountDto(
        "acct-1",
        "ASSET.PLATFORM.FLOAT",
        "Platform Float",
        "ASSET",
        "DEBIT",
        "GHS",
        "ACTIVE",
        "SYSTEM",
        null,
        null,
        "2026-01-01T00:00:00",
        "2026-01-01T00:00:00");
  }

  @Nested
  @DisplayName("GET /api/v0/ledger/accounts")
  class ListAccounts {

    @Test
    @WithMockUser
    @DisplayName("returns 200 with paginated account list")
    void returns200WithAccounts() throws Exception {
      var page = new LedgerPageDto<>(List.of(sampleAccount()), 1, 1L, 0, 20);
      when(ledgerClient.listAccounts(
              any(), isNull(), isNull(), isNull(), isNull(), anyInt(), anyInt()))
          .thenReturn(page);

      mockMvc
          .perform(get("/api/v0/ledger/accounts").accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.items[0].accountId").value("acct-1"))
          .andExpect(jsonPath("$.items[0].accountCode").value("ASSET.PLATFORM.FLOAT"));
    }

    @Test
    @WithMockUser
    @DisplayName("circuit breaker open returns 500")
    void circuitBreakerOpen_returns500() throws Exception {
      when(ledgerClient.listAccounts(any(), any(), any(), any(), any(), anyInt(), anyInt()))
          .thenThrow(new CircuitBreakerOpenException("OPEN"));

      mockMvc
          .perform(get("/api/v0/ledger/accounts").accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isInternalServerError())
          .andExpect(jsonPath("$.message").value("Ledger service temporarily unavailable"));
    }
  }

  @Nested
  @DisplayName("GET /api/v0/ledger/accounts/{id}")
  class GetAccount {

    @Test
    @WithMockUser
    @DisplayName("returns 200 with account")
    void returns200WithAccount() throws Exception {
      when(ledgerClient.getAccount(anyString(), anyString())).thenReturn(sampleAccount());

      mockMvc
          .perform(get("/api/v0/ledger/accounts/acct-1").accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.accountId").value("acct-1"));
    }
  }

  @Nested
  @DisplayName("GET /api/v0/ledger/accounts/{id}/transactions")
  class GetTransactionHistory {

    private LedgerTransactionHistoryDto sampleRecord() {
      return new LedgerTransactionHistoryDto(
          "line-1",
          "entry-1",
          "2026-01-01T00:00:00Z",
          "ref-1",
          "COLLECTION",
          "COLLECTION",
          "CREDIT",
          new BigDecimal("100.00"),
          "GHS",
          new BigDecimal("100.00"),
          BigDecimal.ZERO,
          BigDecimal.ZERO,
          "Collection completed",
          null,
          1L,
          1L,
          "acct-2",
          List.of());
    }

    @Test
    @WithMockUser
    @DisplayName("returns 200 with cursor page of history records")
    void returns200WithCursorPage() throws Exception {
      var page =
          new LedgerCursorPageDto<>(List.of(sampleRecord()), "next-cursor", null, true, 20);
      when(ledgerClient.getAccountTransactionHistory(
              any(), anyString(), any(), any(), any(), any(), any(), any(), any()))
          .thenReturn(page);

      mockMvc
          .perform(
              get("/api/v0/ledger/accounts/acct-1/transactions")
                  .accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.items[0].lineId").value("line-1"))
          .andExpect(jsonPath("$.items[0].direction").value("CREDIT"))
          .andExpect(jsonPath("$.nextCursor").value("next-cursor"))
          .andExpect(jsonPath("$.hasMore").value(true));
    }

    @Test
    @WithMockUser
    @DisplayName("circuit breaker open returns 500")
    void circuitBreakerOpen_returns500() throws Exception {
      when(ledgerClient.getAccountTransactionHistory(
              any(), anyString(), any(), any(), any(), any(), any(), any(), any()))
          .thenThrow(new CircuitBreakerOpenException("OPEN"));

      mockMvc
          .perform(
              get("/api/v0/ledger/accounts/acct-1/transactions")
                  .accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isInternalServerError())
          .andExpect(jsonPath("$.message").value("Ledger service temporarily unavailable"));
    }
  }

  @Nested
  @DisplayName("GET /api/v0/ledger/transactions/{ref}")
  class GetTransactionByReference {

    private LedgerTransactionReferenceDto sampleLeg() {
      return new LedgerTransactionReferenceDto(
          "line-1",
          "entry-1",
          "acct-1",
          "ORGANIZATION",
          "org-1",
          "2026-01-01T00:00:00Z",
          "ref-1",
          "TRANSFER",
          "TRANSFER",
          "DEBIT",
          new BigDecimal("50.00"),
          "GHS",
          new BigDecimal("50.00"),
          BigDecimal.ZERO,
          BigDecimal.ZERO,
          "Internal transfer",
          null,
          1L,
          1L);
    }

    @Test
    @WithMockUser
    @DisplayName("returns 200 with the transaction legs")
    void returns200WithLegs() throws Exception {
      var page = new LedgerPageDto<>(List.of(sampleLeg()), 1, 1L, 0, 20);
      when(ledgerClient.getTransactionByReference(any(), anyString())).thenReturn(page);

      mockMvc
          .perform(
              get("/api/v0/ledger/transactions/ref-1").accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.items[0].lineId").value("line-1"))
          .andExpect(jsonPath("$.items[0].transactionRef").value("ref-1"))
          .andExpect(jsonPath("$.items[0].direction").value("DEBIT"));
    }

    @Test
    @WithMockUser
    @DisplayName("circuit breaker open returns 500")
    void circuitBreakerOpen_returns500() throws Exception {
      when(ledgerClient.getTransactionByReference(any(), anyString()))
          .thenThrow(new CircuitBreakerOpenException("OPEN"));

      mockMvc
          .perform(
              get("/api/v0/ledger/transactions/ref-1").accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isInternalServerError())
          .andExpect(jsonPath("$.message").value("Ledger service temporarily unavailable"));
    }
  }
}
