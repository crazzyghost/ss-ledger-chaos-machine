package com.softspark.chaos.auth;

import com.softspark.chaos.auth.dto.TokenVerificationResult;
import com.softspark.chaos.exception.UnauthorizedException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Verifies bearer tokens against the AUTH SERVICE, with a short-TTL cache keyed by token hash
 * to reduce repeated introspection calls.
 *
 * <p>When {@code auth-service.client-auth.enabled=false} (dev mode) every token is accepted
 * without contacting the auth service; a static dev principal is returned instead.
 */
@Component
public class TokenVerifier {

  private static final Logger log = LoggerFactory.getLogger(TokenVerifier.class);

  private static final TokenVerificationResult DEV_RESULT =
      new TokenVerificationResult(true, "dev-user", List.of("ROLE_USER"));

  private final AuthService authService;
  private final AuthProperties authProperties;
  private final ConcurrentHashMap<String, CachedVerification> cache = new ConcurrentHashMap<>();

  /**
   * Constructs the verifier.
   *
   * @param authService the underlying auth service client
   * @param authProperties bound auth configuration
   */
  public TokenVerifier(AuthService authService, AuthProperties authProperties) {
    this.authService = authService;
    this.authProperties = authProperties;
  }

  /**
   * Verifies a bearer token, returning the verification result.
   *
   * <p>In dev mode (client-auth disabled) returns a fixed dev principal without calling the auth
   * service. Otherwise checks the in-process cache before issuing a remote introspection call.
   *
   * @param token the raw bearer token (without "Bearer " prefix)
   * @return the verification result
   * @throws UnauthorizedException if the token is invalid or the cache/auth-service says inactive
   */
  public TokenVerificationResult verify(String token) {
    if (!authProperties.clientAuth().enabled()) {
      return DEV_RESULT;
    }

    var key = hashToken(token);
    var cached = cache.get(key);
    if (cached != null && !cached.isExpired()) {
      return cached.result();
    }

    var result = authService.verifyToken(token);
    int ttl =
        authProperties.verificationCacheTtlSeconds() > 0
            ? authProperties.verificationCacheTtlSeconds()
            : 30;
    cache.put(key, new CachedVerification(result, Instant.now().plusSeconds(ttl)));
    return result;
  }

  private String hashToken(String token) {
    try {
      var digest = MessageDigest.getInstance("SHA-256");
      var hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }

  private record CachedVerification(TokenVerificationResult result, Instant expiresAt) {
    boolean isExpired() {
      return Instant.now().isAfter(expiresAt);
    }
  }
}
