package com.softspark.chaos.auth;

import com.softspark.chaos.auth.dto.*;
import com.softspark.chaos.exception.InternalServerErrorException;
import com.softspark.chaos.exception.UnauthorizedException;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Service that proxies authentication operations to the external AUTH SERVICE.
 *
 * <p>Maps auth-service 4xx responses to {@link UnauthorizedException} and unreachable/5xx errors
 * to {@link InternalServerErrorException} so callers never see raw HTTP client exceptions.
 */
@Service
public class AuthService {

  private static final Logger log = LoggerFactory.getLogger(AuthService.class);

  private final RestClient authRestClient;
  private final AuthProperties authProperties;

  /**
   * Constructs the service.
   *
   * @param authRestClient the auth-service-dedicated {@link RestClient}
   * @param authProperties bound auth-service configuration
   */
  public AuthService(
      @Qualifier("authRestClient") RestClient authRestClient, AuthProperties authProperties) {
    this.authRestClient = authRestClient;
    this.authProperties = authProperties;
  }

  /**
   * Forwards login credentials to the AUTH SERVICE and returns the resulting token.
   *
   * @param request the login request with email and password
   * @return the login response containing the access token
   * @throws UnauthorizedException if the auth service rejects the credentials
   * @throws InternalServerErrorException if the auth service is unreachable
   */
  public LoginResponse login(LoginRequest request) {
    try {
      var loginUri =
          authProperties.loginUri() != null && !authProperties.loginUri().isBlank()
              ? authProperties.loginUri()
              : "/realm/login";

      var requestBody = new HashMap<String, Object>();
      requestBody.put("email", request.email());
      requestBody.put("password", request.password());
      if (authProperties.realmId() != null && !authProperties.realmId().isBlank()) {
        requestBody.put("realm_id", authProperties.realmId());
      }

      var authServiceLoginResponse =
          authRestClient
              .post()
              .uri(loginUri)
              .body(requestBody)
              .retrieve()
              .onStatus(
                  HttpStatusCode::is4xxClientError,
                  (req, resp) -> {
                    throw new UnauthorizedException("Invalid credentials");
                  })
              .onStatus(
                  HttpStatusCode::is5xxServerError,
                  (req, resp) -> {
                    throw new InternalServerErrorException("Auth service unavailable");
                  })
              .body(AuthServiceLoginResponse.class);

      return LoginResponseBuilder.builder()
          .accessToken(authServiceLoginResponse.data().accessToken())
          .refreshToken(authServiceLoginResponse.data().refreshToken())
          .tokenType("access")
          .build();
    } catch (UnauthorizedException | InternalServerErrorException e) {
      throw e;
    } catch (ResourceAccessException e) {
      throw new InternalServerErrorException("Auth service unreachable");
    } catch (Exception e) {
      log.warn("Auth service login error: {}", e.getMessage());
      throw new InternalServerErrorException("Auth service unavailable");
    }
  }

  /**
   * Introspects a bearer token against the AUTH SERVICE.
   *
   * @param token the raw bearer token to verify
   * @return the verification result indicating active status and principal
   * @throws InternalServerErrorException if the auth service is unreachable
   */
  public TokenVerificationResult verifyToken(String token) {
    try {
      var verifyUri =
          authProperties.tokenVerificationUri() != null
                  && !authProperties.tokenVerificationUri().isBlank()
              ? authProperties.tokenVerificationUri()
              : "/auth/verify";
      return authRestClient
          .post()
          .uri(verifyUri)
          .body(Map.of("token", token))
          .retrieve()
          .onStatus(
              HttpStatusCode::is4xxClientError,
              (req, resp) -> {
                throw new UnauthorizedException("Token invalid or expired");
              })
          .onStatus(
              HttpStatusCode::is5xxServerError,
              (req, resp) -> {
                throw new InternalServerErrorException("Auth service unavailable");
              })
          .body(TokenVerificationResult.class);
    } catch (UnauthorizedException | InternalServerErrorException e) {
      throw e;
    } catch (ResourceAccessException e) {
      throw new InternalServerErrorException("Auth service unreachable");
    } catch (Exception e) {
      log.warn("Token verification error: {}", e.getMessage());
      throw new InternalServerErrorException("Auth service unavailable");
    }
  }

  /**
   * Exchanges a refresh token for a new access token via the AUTH SERVICE.
   *
   * @param request the refresh request containing the refresh token
   * @return the new login response
   * @throws UnauthorizedException if the refresh token is invalid or expired
   * @throws InternalServerErrorException if the auth service is unreachable
   */
  public LoginResponse refresh(RefreshRequest request) {
    try {
      return authRestClient
          .post()
          .uri("/auth/refresh")
          .body(request)
          .retrieve()
          .onStatus(
              HttpStatusCode::is4xxClientError,
              (req, resp) -> {
                throw new UnauthorizedException("Refresh token invalid or expired");
              })
          .onStatus(
              HttpStatusCode::is5xxServerError,
              (req, resp) -> {
                throw new InternalServerErrorException("Auth service unavailable");
              })
          .body(LoginResponse.class);
    } catch (UnauthorizedException | InternalServerErrorException e) {
      throw e;
    } catch (ResourceAccessException e) {
      throw new InternalServerErrorException("Auth service unreachable");
    } catch (Exception e) {
      log.warn("Auth service refresh error: {}", e.getMessage());
      throw new InternalServerErrorException("Auth service unavailable");
    }
  }
}
