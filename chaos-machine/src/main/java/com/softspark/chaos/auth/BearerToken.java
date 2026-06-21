package com.softspark.chaos.auth;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Utility for extracting the {@code Bearer} access token from an inbound request.
 *
 * <p>Used by controllers that forward the caller's logged-in token to the ledger (e.g. the manual
 * chart-of-accounts bootstrap and virtual-account creation), so ledger calls are authorized as the
 * acting user rather than with the static service token from configuration.
 */
public final class BearerToken {

  private BearerToken() {}

  /**
   * Returns the bearer token from the {@code Authorization} header, or {@code null} when absent.
   *
   * @param request the current HTTP request
   * @return the raw token (without the {@code Bearer } prefix), or {@code null}
   */
  public static String fromRequest(HttpServletRequest request) {
    if (request == null) {
      return null;
    }
    var header = request.getHeader("Authorization");
    return header != null && header.startsWith("Bearer ") ? header.substring(7) : null;
  }
}
