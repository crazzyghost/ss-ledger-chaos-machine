package com.softspark.chaos.auth;

import com.softspark.chaos.exception.UnauthorizedException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Servlet filter that extracts a {@code Bearer} token from the {@code Authorization} header,
 * verifies it via {@link TokenVerifier}, and populates the {@link SecurityContextHolder}.
 *
 * <p>Skips processing when the security context already holds an authenticated non-anonymous
 * principal (e.g. during {@code @WithMockUser} tests). This filter is registered as a Spring bean
 * in {@link com.softspark.chaos.config.SecurityConfiguration} and is explicitly wired into the
 * Spring Security filter chain; it is never auto-registered as a generic servlet filter.
 */
public class AccessTokenFilter extends OncePerRequestFilter {

  private static final Logger log = LoggerFactory.getLogger(AccessTokenFilter.class);

  private final TokenVerifier tokenVerifier;

  /**
   * Constructs the filter.
   *
   * @param tokenVerifier the token verifier to use for introspection
   */
  public AccessTokenFilter(TokenVerifier tokenVerifier) {
    this.tokenVerifier = tokenVerifier;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {

    // Skip if a non-anonymous principal is already authenticated (handles @WithMockUser tests).
    var existing = SecurityContextHolder.getContext().getAuthentication();
    if (existing != null
        && existing.isAuthenticated()
        && !(existing instanceof AnonymousAuthenticationToken)) {
      chain.doFilter(request, response);
      return;
    }

    var authHeader = request.getHeader("Authorization");
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      chain.doFilter(request, response);
      return;
    }

    var token = authHeader.substring(7);
    try {
      var result = tokenVerifier.verify(token);
      if (result.active()) {
        List<SimpleGrantedAuthority> authorities =
            result.authorities() != null
                ? result.authorities().stream().map(SimpleGrantedAuthority::new).toList()
                : List.of(new SimpleGrantedAuthority("ROLE_USER"));
        var auth = new UsernamePasswordAuthenticationToken(result.subject(), null, authorities);
        SecurityContextHolder.getContext().setAuthentication(auth);
      }
    } catch (UnauthorizedException e) {
      // Token rejected — leave context empty; Spring Security will deny access.
    } catch (Exception e) {
      log.warn("Token verification failed: {}", e.getMessage());
    }

    chain.doFilter(request, response);
  }
}
