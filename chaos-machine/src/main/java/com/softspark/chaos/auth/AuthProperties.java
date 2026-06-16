package com.softspark.chaos.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the external AUTH SERVICE integration.
 *
 * @param baseUrl the base URL of the auth service
 * @param loginUri the path to the login endpoint
 * @param tokenVerificationUri the path to the token introspection endpoint
 * @param verificationCacheTtlSeconds how long a verified token result is cached (default 30)
 * @param clientAuth client-auth toggle; set to false for permissive dev mode
 */
@ConfigurationProperties(prefix = "auth-service")
public record AuthProperties(
    String baseUrl,
    String loginUri,
    String tokenVerificationUri,
    int verificationCacheTtlSeconds,
    ClientAuth clientAuth,
    String realmId) {

  /** Toggle for enabling real token verification vs. permissive dev mode. */
  public record ClientAuth(boolean enabled) {}
}
