package com.softspark.chaos.organization.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.softspark.chaos.advice.GlobalExceptionHandler;
import com.softspark.chaos.config.SecurityConfiguration;
import com.softspark.chaos.exception.ConflictException;
import com.softspark.chaos.exception.NotFoundException;
import com.softspark.chaos.organization.dto.CreateSupportedCountryRequest;
import com.softspark.chaos.organization.dto.SupportedCountryResponse;
import com.softspark.chaos.organization.enumeration.SupportedCountryStatus;
import com.softspark.chaos.organization.service.SupportedCountryService;
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
 * WebMvc slice tests for {@link SupportedCountryController}.
 */
@WebMvcTest(SupportedCountryController.class)
@Import({GlobalExceptionHandler.class, SecurityConfiguration.class})
@DisplayName("SupportedCountryController")
class SupportedCountryControllerTest {

  @Autowired private MockMvc mockMvc;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @MockitoBean private SupportedCountryService supportedCountryService;

  private SupportedCountryResponse sampleResponse() {
    return new SupportedCountryResponse(
        "11111111-1111-4111-8111-111111111111",
        "country-1",
        SupportedCountryStatus.ACTIVE,
        new SupportedCountryResponse.Country("country-1", "Ghana", "GH", null),
        Instant.now(),
        Instant.now());
  }

  @Nested
  @DisplayName("POST /api/v0/supported-countries")
  class CreateTests {

    @Test
    @WithMockUser
    @DisplayName("valid request returns 201")
    void validRequestReturns201() throws Exception {
      var req = new CreateSupportedCountryRequest("country-1", null);
      when(supportedCountryService.createSupportedCountry(any())).thenReturn(sampleResponse());

      mockMvc
          .perform(
              post("/api/v0/supported-countries")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(req)))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.countryId").value("country-1"));
    }

    @Test
    @WithMockUser
    @DisplayName("blank country id returns 400")
    void blankCountryIdReturns400() throws Exception {
      var req = new CreateSupportedCountryRequest("", null);

      mockMvc
          .perform(
              post("/api/v0/supported-countries")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(req)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.message").value("Validation failed"));
    }

    @Test
    @WithMockUser
    @DisplayName("duplicate country returns 409")
    void duplicateReturns409() throws Exception {
      var req = new CreateSupportedCountryRequest("country-1", null);
      when(supportedCountryService.createSupportedCountry(any()))
          .thenThrow(new ConflictException("Country already supported: country-1"));

      mockMvc
          .perform(
              post("/api/v0/supported-countries")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(req)))
          .andExpect(status().isConflict());
    }
  }

  @Nested
  @DisplayName("DELETE /api/v0/supported-countries/{id}")
  class DeleteTests {

    @Test
    @WithMockUser
    @DisplayName("returns 204 on success")
    void deleteReturns204() throws Exception {
      mockMvc
          .perform(delete("/api/v0/supported-countries/sc-1"))
          .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser
    @DisplayName("returns 404 when missing")
    void deleteReturns404() throws Exception {
      doThrow(new NotFoundException("Supported country not found: missing"))
          .when(supportedCountryService)
          .deleteSupportedCountry("missing");

      mockMvc
          .perform(delete("/api/v0/supported-countries/missing"))
          .andExpect(status().isNotFound());
    }
  }
}
