package com.softspark.chaos.account.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.softspark.chaos.account.dto.CreateVirtualAccountRequest;
import com.softspark.chaos.account.dto.VirtualAccountResponse;
import com.softspark.chaos.account.enumeration.AccountOwnershipType;
import com.softspark.chaos.account.enumeration.AccountStatus;
import com.softspark.chaos.account.enumeration.CreatedVia;
import com.softspark.chaos.account.service.VirtualAccountAnnouncer;
import com.softspark.chaos.account.service.VirtualAccountService;
import com.softspark.chaos.advice.GlobalExceptionHandler;
import com.softspark.chaos.base.PageResponse;
import com.softspark.chaos.config.SecurityConfiguration;
import com.softspark.chaos.exception.NotFoundException;
import java.time.Instant;
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
 * WebMvc slice tests for {@link VirtualAccountController}.
 */
@WebMvcTest(VirtualAccountController.class)
@Import({GlobalExceptionHandler.class, SecurityConfiguration.class})
@DisplayName("VirtualAccountController")
class VirtualAccountControllerTest {

  @Autowired private MockMvc mockMvc;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @MockitoBean private VirtualAccountService virtualAccountService;
  @MockitoBean private VirtualAccountAnnouncer virtualAccountAnnouncer;

  private VirtualAccountResponse sampleResponse() {
    return new VirtualAccountResponse(
        "VA-001",
        "Test Account",
        AccountOwnershipType.ORGANIZATION,
        "org-123",
        "GHS",
        AccountStatus.ACTIVE,
        null,
        null,
        CreatedVia.API,
        Instant.now(),
        Instant.now());
  }

  // ── POST /api/v0/virtual-accounts ─────────────────────────────────────────

  @Nested
  @DisplayName("POST /api/v0/virtual-accounts")
  class CreateVirtualAccountTests {

    @Test
    @WithMockUser
    @DisplayName("valid request returns 201")
    void validRequestReturns201() throws Exception {
      var req =
          new CreateVirtualAccountRequest(
              "Test Account", "ORGANIZATION", "GHS", "org-123", null, null, null, null, false);
      when(virtualAccountService.createVirtualAccount(any())).thenReturn(sampleResponse());

      mockMvc
          .perform(
              post("/api/v0/virtual-accounts")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(req)))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.vaId").value("VA-001"));
    }

    @Test
    @WithMockUser
    @DisplayName("missing name returns 400 with validation error")
    void missingNameReturns400() throws Exception {
      var req =
          new CreateVirtualAccountRequest(
              "", "ORGANIZATION", "GHS", "org-123", null, null, null, null, false);

      mockMvc
          .perform(
              post("/api/v0/virtual-accounts")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(req)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.message").value("Validation failed"))
          .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    @WithMockUser
    @DisplayName("invalid currency returns 400 with validation error")
    void invalidCurrencyReturns400() throws Exception {
      var req =
          new CreateVirtualAccountRequest(
              "My VA", "ORGANIZATION", "NOTACCY", "org-123", null, null, null, null, false);

      mockMvc
          .perform(
              post("/api/v0/virtual-accounts")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(req)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.message").value("Validation failed"));
    }
  }

  // ── GET /api/v0/virtual-accounts/{vaId} ───────────────────────────────────

  @Nested
  @DisplayName("GET /api/v0/virtual-accounts/{vaId}")
  class GetVirtualAccountTests {

    @Test
    @WithMockUser
    @DisplayName("found VA returns 200")
    void foundReturns200() throws Exception {
      when(virtualAccountService.getVirtualAccount("VA-001")).thenReturn(sampleResponse());

      mockMvc
          .perform(get("/api/v0/virtual-accounts/VA-001").accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.vaId").value("VA-001"));
    }

    @Test
    @WithMockUser
    @DisplayName("unknown VA returns 404 with ApiError")
    void unknownReturns404() throws Exception {
      when(virtualAccountService.getVirtualAccount("VA-MISSING"))
          .thenThrow(new NotFoundException("Virtual account not found: VA-MISSING"));

      mockMvc
          .perform(get("/api/v0/virtual-accounts/VA-MISSING").accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.message").value("Virtual account not found: VA-MISSING"));
    }
  }

  // ── GET /api/v0/virtual-accounts ──────────────────────────────────────────

  @Nested
  @DisplayName("GET /api/v0/virtual-accounts")
  class ListVirtualAccountsTests {

    @Test
    @WithMockUser
    @DisplayName("returns paginated list")
    void returnsPaginatedList() throws Exception {
      var page = new PageResponse<>(List.of(sampleResponse()), 0, 20, 1L);
      when(virtualAccountService.listVirtualAccounts(
              any(), any(), any(), any(), any(), any(), any()))
          .thenReturn(page);

      mockMvc
          .perform(get("/api/v0/virtual-accounts").accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.items").isArray())
          .andExpect(jsonPath("$.items[0].vaId").value("VA-001"))
          .andExpect(jsonPath("$.total").value(1));
    }
  }

  // ── POST /api/v0/virtual-accounts/{vaId}/publish ──────────────────────────

  @Nested
  @DisplayName("POST /api/v0/virtual-accounts/{vaId}/publish")
  class PublishVirtualAccountTests {

    @Test
    @WithMockUser
    @DisplayName("successful publish returns 204")
    void successReturns204() throws Exception {
      doNothing().when(virtualAccountAnnouncer).announceVirtualAccount(anyString());

      mockMvc
          .perform(post("/api/v0/virtual-accounts/VA-001/publish"))
          .andExpect(status().isNoContent());
    }
  }
}
