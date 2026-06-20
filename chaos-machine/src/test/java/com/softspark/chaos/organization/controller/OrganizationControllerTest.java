package com.softspark.chaos.organization.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.softspark.chaos.advice.GlobalExceptionHandler;
import com.softspark.chaos.config.SecurityConfiguration;
import com.softspark.chaos.organization.dto.CreateOrganizationRequest;
import com.softspark.chaos.organization.dto.OrganizationResponse;
import com.softspark.chaos.organization.enumeration.OrganizationStatus;
import com.softspark.chaos.organization.service.OrganizationService;
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
 * WebMvc slice tests for {@link OrganizationController}.
 */
@WebMvcTest(OrganizationController.class)
@Import({GlobalExceptionHandler.class, SecurityConfiguration.class})
@DisplayName("OrganizationController")
class OrganizationControllerTest {

  @Autowired private MockMvc mockMvc;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @MockitoBean private OrganizationService organizationService;

  private OrganizationResponse sampleResponse() {
    return new OrganizationResponse(
        "11111111-1111-4111-8111-111111111111",
        "Acme Ltd",
        "type-1",
        "country-1",
        "Merchant",
        "Ghana",
        "GH",
        "ACTIVE",
        Instant.now(),
        "ops@acme.test",
        List.of("+233200000000"),
        OrganizationStatus.ACTIVE,
        Instant.now(),
        Instant.now(),
        "22222222-2222-4222-8222-222222222222");
  }

  // ── POST /api/v0/organizations ───────────────────────────────────────────────

  @Nested
  @DisplayName("POST /api/v0/organizations")
  class OnboardOrganizationTests {

    @Test
    @WithMockUser
    @DisplayName("valid request returns 201")
    void validRequestReturns201() throws Exception {
      var req =
          new CreateOrganizationRequest(
              "Acme Ltd", "type-1", "country-1", "ops@acme.test", List.of("+233200000000"), null);
      when(organizationService.onboard(any())).thenReturn(sampleResponse());

      mockMvc
          .perform(
              post("/api/v0/organizations")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(req)))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.name").value("Acme Ltd"))
          .andExpect(jsonPath("$.countryName").value("Ghana"))
          .andExpect(jsonPath("$.phoneNumbers[0]").value("+233200000000"))
          .andExpect(jsonPath("$.eventId").value("22222222-2222-4222-8222-222222222222"));
    }

    @Test
    @WithMockUser
    @DisplayName("blank name returns 400 with validation error")
    void blankNameReturns400() throws Exception {
      var req = new CreateOrganizationRequest("", "type-1", "country-1", null, null, null);

      mockMvc
          .perform(
              post("/api/v0/organizations")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(req)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.message").value("Validation failed"))
          .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    @WithMockUser
    @DisplayName("blank organization type id returns 400")
    void blankTypeReturns400() throws Exception {
      var req = new CreateOrganizationRequest("Acme Ltd", "", "country-1", null, null, null);

      mockMvc
          .perform(
              post("/api/v0/organizations")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(req)))
          .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    @DisplayName("blank country id returns 400")
    void blankCountryReturns400() throws Exception {
      var req = new CreateOrganizationRequest("Acme Ltd", "type-1", "", null, null, null);

      mockMvc
          .perform(
              post("/api/v0/organizations")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(req)))
          .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    @DisplayName("invalid email returns 400")
    void invalidEmailReturns400() throws Exception {
      var req =
          new CreateOrganizationRequest(
              "Acme Ltd", "type-1", "country-1", "not-an-email", null, null);

      mockMvc
          .perform(
              post("/api/v0/organizations")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(req)))
          .andExpect(status().isBadRequest());
    }
  }
}
