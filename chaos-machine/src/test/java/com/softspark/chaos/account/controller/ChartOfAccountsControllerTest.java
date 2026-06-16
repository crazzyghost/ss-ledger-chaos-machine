package com.softspark.chaos.account.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.softspark.chaos.account.dto.ChartOfAccountsRoleResponse;
import com.softspark.chaos.account.dto.UpdateRoleRequest;
import com.softspark.chaos.account.enumeration.AccountCategory;
import com.softspark.chaos.account.enumeration.AccountRole;
import com.softspark.chaos.account.enumeration.ProvisioningStatus;
import com.softspark.chaos.account.service.ChartOfAccountsService;
import com.softspark.chaos.advice.GlobalExceptionHandler;
import com.softspark.chaos.config.SecurityConfiguration;
import com.softspark.chaos.exception.NotFoundException;
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
 * WebMvc slice tests for {@link ChartOfAccountsController}.
 */
@WebMvcTest(ChartOfAccountsController.class)
@Import({GlobalExceptionHandler.class, SecurityConfiguration.class})
@DisplayName("ChartOfAccountsController")
class ChartOfAccountsControllerTest {

  @Autowired private MockMvc mockMvc;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @MockitoBean private ChartOfAccountsService chartOfAccountsService;

  private ChartOfAccountsRoleResponse sampleRole() {
    return new ChartOfAccountsRoleResponse(
        AccountRole.PLATFORM_FLOAT,
        "ASSET.PLATFORM.FLOAT",
        AccountCategory.ASSET,
        "GHS",
        null,
        "VA-FLOAT",
        "VA-FLOAT",
        ProvisioningStatus.PROVISIONED);
  }

  // ── GET /api/v0/chart-of-accounts ─────────────────────────────────────────

  @Nested
  @DisplayName("GET /api/v0/chart-of-accounts")
  class GetAllRolesTests {

    @Test
    @WithMockUser
    @DisplayName("returns list of roles with 200")
    void returnsRolesList() throws Exception {
      when(chartOfAccountsService.getAllRoles()).thenReturn(List.of(sampleRole()));

      mockMvc
          .perform(get("/api/v0/chart-of-accounts").accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$[0].accountCode").value("ASSET.PLATFORM.FLOAT"));
    }
  }

  // ── PUT /api/v0/chart-of-accounts/{role} ──────────────────────────────────

  @Nested
  @DisplayName("PUT /api/v0/chart-of-accounts/{role}")
  class UpdateRoleTests {

    @Test
    @WithMockUser
    @DisplayName("valid update returns 200 with updated role")
    void validUpdateReturns200() throws Exception {
      var updated =
          new ChartOfAccountsRoleResponse(
              AccountRole.PLATFORM_FLOAT,
              "ASSET.PLATFORM.FLOAT",
              AccountCategory.ASSET,
              "GHS",
              null,
              "VA-NEW",
              "VA-NEW",
              ProvisioningStatus.PROVISIONED);
      when(chartOfAccountsService.updateRole(any(), any())).thenReturn(updated);

      var req = new UpdateRoleRequest("VA-NEW", "GHS");

      mockMvc
          .perform(
              put("/api/v0/chart-of-accounts/PLATFORM_FLOAT")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(req)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.defaultVaId").value("VA-NEW"));
    }

    @Test
    @WithMockUser
    @DisplayName("unknown role returns 404 with ApiError")
    void unknownRoleReturns404() throws Exception {
      when(chartOfAccountsService.updateRole(any(), any()))
          .thenThrow(new NotFoundException("Account role not found: PLATFORM_FLOAT"));

      var req = new UpdateRoleRequest("VA-NEW", "GHS");

      mockMvc
          .perform(
              put("/api/v0/chart-of-accounts/PLATFORM_FLOAT")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(req)))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.message").value("Account role not found: PLATFORM_FLOAT"));
    }
  }
}
