package com.softspark.chaos.advice;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.softspark.chaos.exception.ConflictException;
import com.softspark.chaos.exception.NotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * WebMvc slice tests for {@link GlobalExceptionHandler}.
 *
 * <p>A minimal stub controller inside the test class provides endpoints that deliberately throw
 * each exception type, so the handler's response shape can be asserted without a real controller.
 */
@WebMvcTest(controllers = GlobalExceptionHandlerTest.StubController.class)
@Import(GlobalExceptionHandler.class)
@DisplayName("GlobalExceptionHandler")
class GlobalExceptionHandlerTest {

  @Autowired private MockMvc mockMvc;

  // ── Stub controller ────────────────────────────────────────────────────────

  /**
   * Minimal stub that exposes one endpoint per exception type.
   */
  @RestController
  @RequestMapping("/test")
  @Validated
  static class StubController {

    @GetMapping("/not-found")
    public void notFound() {
      throw new NotFoundException("Resource not found");
    }

    @GetMapping("/conflict")
    public void conflict() {
      throw new ConflictException("Resource already exists");
    }

    @GetMapping("/access-denied")
    public void accessDenied() {
      throw new AccessDeniedException("Access denied");
    }

    @GetMapping("/internal-error")
    public void internalError() {
      throw new RuntimeException("Unexpected failure");
    }

    @GetMapping("/validation")
    public void validationError(@RequestParam @Valid @NotBlank String name) {}
  }

  // ── Tests ──────────────────────────────────────────────────────────────────

  @Nested
  @DisplayName("404 NotFoundException")
  class NotFoundTests {

    @Test
    @WithMockUser
    @DisplayName("returns 404 with ApiError message")
    void returnsNotFound() throws Exception {
      mockMvc
          .perform(get("/test/not-found").accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.message").value("Resource not found"))
          .andExpect(jsonPath("$.errors").isArray());
    }
  }

  @Nested
  @DisplayName("409 ConflictException")
  class ConflictTests {

    @Test
    @WithMockUser
    @DisplayName("returns 409 with ApiError message")
    void returnsConflict() throws Exception {
      mockMvc
          .perform(get("/test/conflict").accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isConflict())
          .andExpect(jsonPath("$.message").value("Resource already exists"))
          .andExpect(jsonPath("$.errors").isArray());
    }
  }

  @Nested
  @DisplayName("403 AccessDeniedException")
  class AccessDeniedTests {

    @Test
    @WithMockUser
    @DisplayName("returns 403 with 'Access denied' message")
    void returnsAccessDenied() throws Exception {
      mockMvc
          .perform(get("/test/access-denied").accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.message").value("Access denied"));
    }
  }

  @Nested
  @DisplayName("500 unhandled RuntimeException")
  class InternalErrorTests {

    @Test
    @WithMockUser
    @DisplayName("returns 500 with generic internal error message")
    void returnsInternalServerError() throws Exception {
      mockMvc
          .perform(get("/test/internal-error").accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isInternalServerError())
          .andExpect(jsonPath("$.message").value("An internal error occurred"));
    }
  }
}
