package com.softspark.chaos.auth;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.softspark.chaos.advice.GlobalExceptionHandler;
import com.softspark.chaos.auth.dto.LoginRequest;
import com.softspark.chaos.auth.dto.LoginResponse;
import com.softspark.chaos.auth.dto.RefreshRequest;
import com.softspark.chaos.config.SecurityConfiguration;
import com.softspark.chaos.exception.UnauthorizedException;
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
 * WebMvc slice tests for {@link AuthController}.
 */
@WebMvcTest(AuthController.class)
@Import({GlobalExceptionHandler.class, SecurityConfiguration.class})
@DisplayName("AuthController")
class AuthControllerTest {

  @Autowired private MockMvc mockMvc;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @MockitoBean private AuthService authService;

  @Nested
  @DisplayName("POST /api/v0/auth/login")
  class LoginTests {

    @Test
    @DisplayName("valid credentials return 200 with access token")
    void validCredentials_returns200() throws Exception {
      var response = new LoginResponse("tok123", "Bearer", 3600L, null);
      when(authService.login(new LoginRequest("user@example.com", "secret"))).thenReturn(response);

      mockMvc
          .perform(
              post("/api/v0/auth/login")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      objectMapper.writeValueAsString(
                          new LoginRequest("user@example.com", "secret"))))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.access_token").value("tok123"))
          .andExpect(jsonPath("$.token_type").value("Bearer"));
    }

    @Test
    @DisplayName("bad credentials return 401")
    void badCredentials_returns401() throws Exception {
      when(authService.login(new LoginRequest("user@example.com", "wrong")))
          .thenThrow(new UnauthorizedException("Invalid credentials"));

      mockMvc
          .perform(
              post("/api/v0/auth/login")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      objectMapper.writeValueAsString(
                          new LoginRequest("user@example.com", "wrong"))))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.message").value("Invalid credentials"));
    }
  }

  @Nested
  @DisplayName("POST /api/v0/auth/refresh")
  class RefreshTests {

    @Test
    @DisplayName("valid refresh token returns 200 with new token")
    void validRefresh_returns200() throws Exception {
      var response = new LoginResponse("newtok", "Bearer", 3600L, null);
      when(authService.refresh(new RefreshRequest("refreshme"))).thenReturn(response);

      mockMvc
          .perform(
              post("/api/v0/auth/refresh")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(new RefreshRequest("refreshme"))))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.access_token").value("newtok"));
    }
  }

  @Nested
  @DisplayName("GET /api/v0/auth/me")
  class MeTests {

    @Test
    @WithMockUser(username = "operator@example.com", roles = "USER")
    @DisplayName("authenticated user returns 200 with subject and authorities")
    void authenticatedUser_returnsInfo() throws Exception {
      mockMvc
          .perform(get("/api/v0/auth/me"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.subject").value("operator@example.com"))
          .andExpect(jsonPath("$.authorities[0]").value("ROLE_USER"));

      verify(authService, never()).login(null);
    }
  }
}
