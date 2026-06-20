package com.softspark.chaos.organization.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.softspark.chaos.advice.GlobalExceptionHandler;
import com.softspark.chaos.config.SecurityConfiguration;
import com.softspark.chaos.organization.dto.CreateOrganizationTypeRequest;
import com.softspark.chaos.organization.dto.OrganizationTypeResponse;
import com.softspark.chaos.organization.service.OrganizationTypeService;
import java.time.Instant;
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
 * WebMvc slice tests for {@link OrganizationTypeController}.
 */
@WebMvcTest(OrganizationTypeController.class)
@Import({GlobalExceptionHandler.class, SecurityConfiguration.class})
@DisplayName("OrganizationTypeController")
class OrganizationTypeControllerTest {

  @Autowired private MockMvc mockMvc;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @MockitoBean private OrganizationTypeService organizationTypeService;

  private OrganizationTypeResponse sampleResponse() {
    return new OrganizationTypeResponse(
        "11111111-1111-4111-8111-111111111111", "Business", Instant.now(), Instant.now());
  }

  // ── POST /api/v0/organization-types ─────────────────────────────────────────

  @Nested
  @DisplayName("POST /api/v0/organization-types")
  class CreateOrganizationTypeTests {

    @Test
    @WithMockUser
    @DisplayName("valid request returns 201")
    void validRequestReturns201() throws Exception {
      var req = new CreateOrganizationTypeRequest("Business");
      when(organizationTypeService.createOrganizationType(any())).thenReturn(sampleResponse());

      mockMvc
          .perform(
              post("/api/v0/organization-types")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(req)))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.name").value("Business"));
    }

    @Test
    @WithMockUser
    @DisplayName("blank name returns 400 with validation error")
    void blankNameReturns400() throws Exception {
      var req = new CreateOrganizationTypeRequest("");

      mockMvc
          .perform(
              post("/api/v0/organization-types")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(req)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.message").value("Validation failed"))
          .andExpect(jsonPath("$.errors").isArray());
    }
  }
}
