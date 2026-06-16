package com.softspark.chaos.config;

import com.softspark.chaos.auth.AccessTokenFilter;
import com.softspark.chaos.auth.TokenVerifier;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Spring Security configuration for the chaos machine.
 *
 * <p>Stateless bearer-token auth: every request must carry a valid {@code Authorization: Bearer}
 * token that the {@link AccessTokenFilter} verifies via the external AUTH SERVICE.
 *
 * <p>Public allow-list: login, refresh, actuator/health, OpenAPI docs.
 *
 * <p>{@link TokenVerifier} is injected as optional so that {@code @WebMvcTest} slices — which
 * do not load the full {@code auth} package — can still import this configuration. Slice tests
 * use {@code @WithMockUser} which populates the security context before the filter runs.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

  /**
   * Configures the security filter chain.
   *
   * <p>When {@link TokenVerifier} is present the bearer-token filter is wired into the chain.
   * In {@code @WebMvcTest} slices it is absent and the chain relies on {@code @WithMockUser}.
   *
   * @param http the {@link HttpSecurity} builder
   * @param tokenVerifier the token verifier; {@code null} in test slices
   * @return the configured {@link SecurityFilterChain}
   * @throws Exception if security configuration fails
   */
  @Bean
  public SecurityFilterChain securityFilterChain(
      HttpSecurity http, @Autowired(required = false) TokenVerifier tokenVerifier)
      throws Exception {

    http.csrf(csrf -> csrf.disable())
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(
                        "/api/v0/auth/login",
                        "/api/v0/auth/refresh",
                        "/actuator/health",
                        "/actuator/info",
                        "/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .exceptionHandling(
            ex ->
                ex.authenticationEntryPoint(
                        (request, response, authEx) -> {
                          response.setStatus(401);
                          response.setContentType("application/json");
                          response
                              .getWriter()
                              .write("{\"message\":\"Unauthorized\",\"errors\":[]}");
                        })
                    .accessDeniedHandler(
                        (request, response, accessDeniedEx) -> {
                          response.setStatus(403);
                          response.setContentType("application/json");
                          response.getWriter().write("{\"message\":\"Forbidden\",\"errors\":[]}");
                        }));

    if (tokenVerifier != null) {
      http.addFilterBefore(
          new AccessTokenFilter(tokenVerifier), UsernamePasswordAuthenticationFilter.class);
    }

    return http.build();
  }

  /**
   * CORS configuration allowing all origins (suitable for dev; tighten per-env via config).
   *
   * @return the CORS configuration source
   */
  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    var config = new CorsConfiguration();
    config.setAllowedOriginPatterns(List.of("*"));
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
    config.setAllowedHeaders(List.of("*"));
    config.setAllowCredentials(true);
    var source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
  }
}
