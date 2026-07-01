package com.softspark.chaos.ledgerproxy;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
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
import com.softspark.chaos.ledgerproxy.dto.ReconciliationEntryDto;
import com.softspark.chaos.ledgerproxy.dto.ReconciliationEntryDtoBuilder;
import com.softspark.chaos.ledgerproxy.dto.ReconciliationSiblingLineDtoBuilder;
import com.softspark.chaos.ledgerproxy.dto.TrialBalanceDto;
import com.softspark.chaos.ledgerproxy.dto.TrialBalanceEntryDto;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
          null,
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
      var page = new LedgerCursorPageDto<>(List.of(sampleRecord()), "next-cursor", null, true, 20);
      when(ledgerClient.getAccountTransactionHistory(
              any(), anyString(), any(), any(), any(), any(), any(), any(), any()))
          .thenReturn(page);

      mockMvc
          .perform(
              get("/api/v0/ledger/accounts/acct-1/transactions").accept(MediaType.APPLICATION_JSON))
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
              get("/api/v0/ledger/accounts/acct-1/transactions").accept(MediaType.APPLICATION_JSON))
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
          .perform(get("/api/v0/ledger/transactions/ref-1").accept(MediaType.APPLICATION_JSON))
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
          .perform(get("/api/v0/ledger/transactions/ref-1").accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isInternalServerError())
          .andExpect(jsonPath("$.message").value("Ledger service temporarily unavailable"));
    }
  }

  @Nested
  @DisplayName("GET /api/v0/ledger/reporting/trial-balance")
  class GetTrialBalance {

    private static final String FROM = "2026-06-01T00:00:00Z";
    private static final String TO = "2026-07-01T00:00:00Z";

    private TrialBalanceDto sampleReport() {
      return new TrialBalanceDto(
          Instant.parse(FROM),
          Instant.parse(TO),
          "GHS",
          new BigDecimal("100.00"),
          new BigDecimal("100.00"),
          true,
          1,
          List.of(
              new TrialBalanceEntryDto(
                  "acct-1",
                  "ASSET.PLATFORM.FLOAT",
                  "Platform Float",
                  null,
                  "SYSTEM",
                  "GHS",
                  new BigDecimal("100.00"),
                  new BigDecimal("0.00"),
                  new BigDecimal("100.00"))));
    }

    @Test
    @WithMockUser
    @DisplayName("returns 200 with the trial-balance report and forwards the period")
    void returns200WithReport() throws Exception {
      when(ledgerClient.getTrialBalance(any(), any(), any(), any())).thenReturn(sampleReport());

      mockMvc
          .perform(
              get("/api/v0/ledger/reporting/trial-balance")
                  .param("from", FROM)
                  .param("to", TO)
                  .accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.currency").value("GHS"))
          .andExpect(jsonPath("$.isBalanced").value(true))
          .andExpect(jsonPath("$.numberOfAccounts").value(1))
          .andExpect(jsonPath("$.totalDebits").value("100.00"))
          .andExpect(jsonPath("$.accounts[0].accountCode").value("ASSET.PLATFORM.FLOAT"))
          .andExpect(jsonPath("$.accounts[0].accountOwnershipType").value("SYSTEM"))
          .andExpect(jsonPath("$.accounts[0].accountOwnerId").doesNotExist());

      var fromCaptor = ArgumentCaptor.forClass(Instant.class);
      var toCaptor = ArgumentCaptor.forClass(Instant.class);
      verify(ledgerClient)
          .getTrialBalance(any(), fromCaptor.capture(), toCaptor.capture(), isNull());
      org.assertj.core.api.Assertions.assertThat(fromCaptor.getValue())
          .isEqualTo(Instant.parse(FROM));
      org.assertj.core.api.Assertions.assertThat(toCaptor.getValue()).isEqualTo(Instant.parse(TO));
    }

    @Test
    @WithMockUser
    @DisplayName("forwards the currency query param when present")
    void forwardsCurrency() throws Exception {
      when(ledgerClient.getTrialBalance(any(), any(), any(), eq("GHS"))).thenReturn(sampleReport());

      mockMvc
          .perform(
              get("/api/v0/ledger/reporting/trial-balance")
                  .param("from", FROM)
                  .param("to", TO)
                  .param("currency", "GHS")
                  .accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.currency").value("GHS"));

      verify(ledgerClient).getTrialBalance(any(), any(), any(), eq("GHS"));
    }

    @Test
    @WithMockUser
    @DisplayName("missing required 'from'/'to' yields 400 before any ledger call")
    void missingParams_returns400() throws Exception {
      mockMvc
          .perform(
              get("/api/v0/ledger/reporting/trial-balance")
                  .param("to", TO)
                  .accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    @DisplayName("circuit breaker open returns 500")
    void circuitBreakerOpen_returns500() throws Exception {
      when(ledgerClient.getTrialBalance(any(), any(), any(), any()))
          .thenThrow(new CircuitBreakerOpenException("OPEN"));

      mockMvc
          .perform(
              get("/api/v0/ledger/reporting/trial-balance")
                  .param("from", FROM)
                  .param("to", TO)
                  .accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isInternalServerError())
          .andExpect(jsonPath("$.message").value("Ledger service temporarily unavailable"));
    }
  }

  @Nested
  @DisplayName("GET /api/v0/ledger/reporting/reconciliation-export")
  class ExportJournalEntries {

    private static final String FROM = "2026-06-22T00:00:00Z";
    private static final String TO = "2026-06-29T00:00:00Z";

    private LedgerPageDto<ReconciliationEntryDto> samplePage() {
      var sibling =
          ReconciliationSiblingLineDtoBuilder.builder()
              .lineId("line-2")
              .accountId("acct-2")
              .direction("CREDIT")
              .amount(new BigDecimal("100.00"))
              .currency("GHS")
              .build();
      var entry =
          ReconciliationEntryDtoBuilder.builder()
              .lineId("line-1")
              .journalEntryId("je-1")
              .postedAt("2026-06-23T10:00:00Z")
              .accountId("acct-1")
              .accountCode("ASSET.PLATFORM.FLOAT")
              .direction("DEBIT")
              .amount(new BigDecimal("100.00"))
              .currency("GHS")
              .transactionRef("txn-ref-1")
              .entryType("DISBURSEMENT")
              .sourceService("ss-disbursement-service")
              .siblingLines(List.of(sibling))
              .build();
      return new LedgerPageDto<>(List.of(entry), 1, 1L, 0, 20);
    }

    @Test
    @WithMockUser
    @DisplayName("returns 200 with the paged journal entries and forwards the window")
    void returns200WithEntries() throws Exception {
      when(ledgerClient.exportJournalEntries(
              any(), any(), any(), isNull(), isNull(), isNull(), isNull(), anyInt(), anyInt()))
          .thenReturn(samplePage());

      mockMvc
          .perform(
              get("/api/v0/ledger/reporting/reconciliation-export")
                  .param("from", FROM)
                  .param("to", TO)
                  .accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.total").value(1))
          .andExpect(jsonPath("$.items[0].lineId").value("line-1"))
          .andExpect(jsonPath("$.items[0].transactionRef").value("txn-ref-1"))
          .andExpect(jsonPath("$.items[0].siblingLines[0].accountId").value("acct-2"));

      var fromCaptor = ArgumentCaptor.forClass(Instant.class);
      var toCaptor = ArgumentCaptor.forClass(Instant.class);
      verify(ledgerClient)
          .exportJournalEntries(
              any(),
              fromCaptor.capture(),
              toCaptor.capture(),
              isNull(),
              isNull(),
              isNull(),
              isNull(),
              anyInt(),
              anyInt());
      org.assertj.core.api.Assertions.assertThat(fromCaptor.getValue())
          .isEqualTo(Instant.parse(FROM));
      org.assertj.core.api.Assertions.assertThat(toCaptor.getValue()).isEqualTo(Instant.parse(TO));
    }

    @Test
    @WithMockUser
    @DisplayName("forwards repeatable accountId and optional filters")
    void forwardsFilters() throws Exception {
      when(ledgerClient.exportJournalEntries(
              any(),
              any(),
              any(),
              eq(List.of("acct-1", "acct-2")),
              eq("DISBURSEMENT"),
              eq("txn-ref-1"),
              eq("ss-disbursement-service"),
              anyInt(),
              anyInt()))
          .thenReturn(samplePage());

      mockMvc
          .perform(
              get("/api/v0/ledger/reporting/reconciliation-export")
                  .param("from", FROM)
                  .param("to", TO)
                  .param("accountId", "acct-1", "acct-2")
                  .param("entryType", "DISBURSEMENT")
                  .param("transactionRef", "txn-ref-1")
                  .param("sourceService", "ss-disbursement-service")
                  .accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk());

      verify(ledgerClient)
          .exportJournalEntries(
              any(),
              any(),
              any(),
              eq(List.of("acct-1", "acct-2")),
              eq("DISBURSEMENT"),
              eq("txn-ref-1"),
              eq("ss-disbursement-service"),
              anyInt(),
              anyInt());
    }

    @Test
    @WithMockUser
    @DisplayName("missing required 'from'/'to' yields 400 before any ledger call")
    void missingParams_returns400() throws Exception {
      mockMvc
          .perform(
              get("/api/v0/ledger/reporting/reconciliation-export")
                  .param("to", TO)
                  .accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    @DisplayName("relays the ledger's too-wide-window 4xx as a chaos 4xx (not 500)")
    void ledgerPeriodError_relayedAs4xx() throws Exception {
      when(ledgerClient.exportJournalEntries(
              any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
          .thenThrow(new com.softspark.chaos.exception.NotFoundException("Ledger returned: 400"));

      mockMvc
          .perform(
              get("/api/v0/ledger/reporting/reconciliation-export")
                  .param("from", FROM)
                  .param("to", TO)
                  .accept(MediaType.APPLICATION_JSON))
          .andExpect(status().is4xxClientError());
    }

    @Test
    @WithMockUser
    @DisplayName("circuit breaker open returns 500")
    void circuitBreakerOpen_returns500() throws Exception {
      when(ledgerClient.exportJournalEntries(
              any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
          .thenThrow(new CircuitBreakerOpenException("OPEN"));

      mockMvc
          .perform(
              get("/api/v0/ledger/reporting/reconciliation-export")
                  .param("from", FROM)
                  .param("to", TO)
                  .accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isInternalServerError())
          .andExpect(jsonPath("$.message").value("Ledger service temporarily unavailable"));
    }
  }

  @Nested
  @DisplayName("removed phantom GET /api/v0/ledger/transactions")
  class RemovedTransactionsList {

    @Test
    @WithMockUser
    @DisplayName("the global transactions list is gone (404)")
    void globalTransactionsList_returns404() throws Exception {
      mockMvc
          .perform(get("/api/v0/ledger/transactions").accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isNotFound());
    }
  }
}
