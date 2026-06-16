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
import com.softspark.chaos.ledgerproxy.dto.LedgerPageDto;
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
        null);
  }

  @Nested
  @DisplayName("GET /api/v0/ledger/accounts")
  class ListAccounts {

    @Test
    @WithMockUser
    @DisplayName("returns 200 with paginated account list")
    void returns200WithAccounts() throws Exception {
      var page = new LedgerPageDto<>(List.of(sampleAccount()), 1, 1L, 0, 20);
      when(ledgerClient.listAccounts(any(), isNull(), isNull(), isNull(), anyInt(), anyInt()))
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
      when(ledgerClient.listAccounts(any(), any(), any(), any(), anyInt(), anyInt()))
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
}
