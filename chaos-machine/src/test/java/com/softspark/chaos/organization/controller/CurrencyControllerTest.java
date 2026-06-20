package com.softspark.chaos.organization.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.softspark.chaos.advice.GlobalExceptionHandler;
import com.softspark.chaos.config.SecurityConfiguration;
import com.softspark.chaos.organization.dto.CreateCurrencyRequest;
import com.softspark.chaos.organization.dto.CurrencyResponse;
import com.softspark.chaos.organization.enumeration.CurrencyStatus;
import com.softspark.chaos.organization.service.CurrencyService;
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
 * WebMvc slice tests for {@link CurrencyController}.
 */
@WebMvcTest(CurrencyController.class)
@Import({GlobalExceptionHandler.class, SecurityConfiguration.class})
@DisplayName("CurrencyController")
class CurrencyControllerTest {

  @Autowired private MockMvc mockMvc;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @MockitoBean private CurrencyService currencyService;

  private CurrencyResponse sampleResponse() {
    return new CurrencyResponse(
        "11111111-1111-4111-8111-111111111111",
        "GHS",
        "Ghanaian cedi",
        "₵",
        CurrencyStatus.ACTIVE,
        Instant.now(),
        Instant.now());
  }

  @Nested
  @DisplayName("POST /api/v0/currencies")
  class CreateCurrencyTests {

    @Test
    @WithMockUser
    @DisplayName("valid request returns 201")
    void validRequestReturns201() throws Exception {
      var req = new CreateCurrencyRequest("GHS", "Ghanaian cedi", "₵", null);
      when(currencyService.createCurrency(any())).thenReturn(sampleResponse());

      mockMvc
          .perform(
              post("/api/v0/currencies")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(req)))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.code").value("GHS"));
    }

    @Test
    @WithMockUser
    @DisplayName("blank code returns 400")
    void blankCodeReturns400() throws Exception {
      var req = new CreateCurrencyRequest("", "Ghanaian cedi", "₵", null);

      mockMvc
          .perform(
              post("/api/v0/currencies")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(req)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.message").value("Validation failed"));
    }

    @Test
    @WithMockUser
    @DisplayName("non-ISO-4217 code returns 400")
    void invalidCodeReturns400() throws Exception {
      var req = new CreateCurrencyRequest("ZZZ", "Nonsense", null, null);

      mockMvc
          .perform(
              post("/api/v0/currencies")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(req)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.message").value("Validation failed"));
    }

    @Test
    @WithMockUser
    @DisplayName("invalid status returns 400")
    void invalidStatusReturns400() throws Exception {
      var req = new CreateCurrencyRequest("GHS", "Ghanaian cedi", null, "NOT_A_STATUS");

      mockMvc
          .perform(
              post("/api/v0/currencies")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(req)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.message").value("Validation failed"));
    }
  }
}
