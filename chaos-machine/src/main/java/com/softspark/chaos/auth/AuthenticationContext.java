package com.softspark.chaos.auth;

import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Utility for resolving the current caller's access token from the {@link SecurityContextHolder}.
 *
 * <p>The {@link AccessTokenFilter} stores the verified bearer token as the authentication's
 * credentials. Components that call the ledger on behalf of the acting user (e.g. the manual
 * chart-of-accounts bootstrap and virtual-account creation) read it here instead of threading the
 * token through method parameters — mirroring {@code ss-ledger-service}'s approach.
 *
 * <p>Returns {@code null} when there is no request-bound authentication (startup runner, scheduled
 * reconciler), in which case callers fall back to the statically configured service token.
 */
public final class AuthenticationContext {

  private AuthenticationContext() {}

  /**
   * Returns the current caller's access token, or {@code null} when none is available.
   *
   * @return the raw bearer token from the security context, or {@code null}
   */
  public static String currentAccessToken() {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null) {
      return null;
    }
    return auth.getCredentials() instanceof String token && !token.isBlank() ? token : null;
  }
}
