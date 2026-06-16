package com.softspark.chaos.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.softspark.chaos.auth.dto.TokenVerificationResult;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link TokenVerifier}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TokenVerifier")
class TokenVerifierTest {

  @Mock private AuthService authService;

  private AuthProperties devModeProps;
  private AuthProperties enabledProps;

  @BeforeEach
  void setUp() {
    devModeProps =
        new AuthProperties(
            "http://auth",
            "/realm/login",
            "/realm/access-token/verify",
            30,
            new AuthProperties.ClientAuth(false),
            "test-realm");
    enabledProps =
        new AuthProperties(
            "http://auth",
            "/realm/login",
            "/realm/access-token/verify",
            30,
            new AuthProperties.ClientAuth(true),
            "test-realm");
  }

  @Nested
  @DisplayName("dev mode (client-auth.enabled=false)")
  class DevMode {

    @Test
    @DisplayName("returns dev principal without calling auth service")
    void returnDevPrincipal_withoutCallingAuthService() {
      var verifier = new TokenVerifier(authService, devModeProps);
      var result = verifier.verify("any-token");

      assertThat(result.active()).isTrue();
      assertThat(result.subject()).isEqualTo("dev-user");
      assertThat(result.authorities()).contains("ROLE_USER");
      verify(authService, never()).verifyToken(anyString());
    }
  }

  @Nested
  @DisplayName("client-auth enabled")
  class ClientAuthEnabled {

    private static final TokenVerificationResult ACTIVE_RESULT =
        new TokenVerificationResult(true, "user@example.com", List.of("ROLE_USER"));

    @Test
    @DisplayName("cache miss calls auth service and caches result")
    void cacheMiss_callsAuthServiceAndCaches() {
      when(authService.verifyToken("tok")).thenReturn(ACTIVE_RESULT);
      var verifier = new TokenVerifier(authService, enabledProps);

      var r1 = verifier.verify("tok");
      var r2 = verifier.verify("tok");

      assertThat(r1.subject()).isEqualTo("user@example.com");
      assertThat(r2.subject()).isEqualTo("user@example.com");
      // Second call should be served from cache — auth service called only once.
      verify(authService, times(1)).verifyToken("tok");
    }

    @Test
    @DisplayName("different tokens produce separate cache entries")
    void differentTokens_separateCacheEntries() {
      var resultA = new TokenVerificationResult(true, "a@example.com", List.of("ROLE_USER"));
      var resultB = new TokenVerificationResult(true, "b@example.com", List.of("ROLE_ADMIN"));
      when(authService.verifyToken("tokA")).thenReturn(resultA);
      when(authService.verifyToken("tokB")).thenReturn(resultB);

      var verifier = new TokenVerifier(authService, enabledProps);
      var rA = verifier.verify("tokA");
      var rB = verifier.verify("tokB");

      assertThat(rA.subject()).isEqualTo("a@example.com");
      assertThat(rB.subject()).isEqualTo("b@example.com");
    }
  }
}
