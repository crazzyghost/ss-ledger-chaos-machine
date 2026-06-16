package com.softspark.chaos.auth;

import com.softspark.chaos.auth.dto.LoginRequest;
import com.softspark.chaos.auth.dto.LoginResponse;
import com.softspark.chaos.auth.dto.RefreshRequest;
import com.softspark.chaos.auth.dto.UserInfoResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for authentication operations.
 *
 * <p>Proxies login and token refresh to the external AUTH SERVICE. The {@code /login} and
 * {@code /refresh} endpoints are public; {@code /me} requires an authenticated caller.
 */
@RestController
@RequestMapping("/api/v0/auth")
@Tag(name = "Auth", description = "Authentication proxy endpoints")
public class AuthController {

  private final AuthService authService;

  /**
   * Constructs the controller.
   *
   * @param authService the auth service delegate
   */
  public AuthController(AuthService authService) {
    this.authService = authService;
  }

  /**
   * Proxies a login request to the AUTH SERVICE and returns the resulting token.
   *
   * @param request the login credentials
   * @return the access token response
   */
  @PostMapping("/login")
  @Operation(summary = "Login", description = "Authenticate with the auth service")
  public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
    var response = authService.login(request);
    return ResponseEntity.ok(response);
  }

  /**
   * Exchanges a refresh token for a new access token.
   *
   * @param request the refresh token request
   * @return the new access token response
   */
  @PostMapping("/refresh")
  @Operation(
      summary = "Refresh token",
      description = "Exchange a refresh token for a new access token")
  public ResponseEntity<LoginResponse> refresh(@Valid @RequestBody RefreshRequest request) {
    var response = authService.refresh(request);
    return ResponseEntity.ok(response);
  }

  /**
   * Returns information about the currently authenticated principal.
   *
   * @param authentication the Spring Security authentication object
   * @return the current user's subject and authorities
   */
  @GetMapping("/me")
  @Operation(
      summary = "Current user",
      description = "Returns information about the authenticated principal")
  public ResponseEntity<UserInfoResponse> me(Authentication authentication) {
    List<String> authorities =
        authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
    return ResponseEntity.ok(new UserInfoResponse(authentication.getName(), authorities));
  }
}
