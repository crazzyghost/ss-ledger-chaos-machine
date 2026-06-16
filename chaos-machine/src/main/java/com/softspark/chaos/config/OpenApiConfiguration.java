package com.softspark.chaos.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI configuration for the chaos machine.
 *
 * <p>Configures Swagger UI and API documentation with a JWT bearer-auth security scheme so that
 * operators can authorize requests from the Swagger UI.
 */
@Configuration
@OpenAPIDefinition(
    info =
        @Info(
            title = "Ledger Chaos Machine API",
            description = "API for publishing controlled event sequences to test ledger behavior",
            version = "v0"),
    security = {@SecurityRequirement(name = "bearerAuth")})
@SecurityScheme(
    name = "bearerAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT")
public class OpenApiConfiguration {

  /**
   * Configures the {@link OpenAPI} bean with server entries for local development and the current
   * deployment environment.
   *
   * @return the configured {@link OpenAPI} instance
   */
  @Bean
  public OpenAPI chaosOpenAPI() {
    return new OpenAPI()
        .servers(
            List.of(
                new Server().url("http://localhost:27100").description("Local development"),
                new Server().url("/").description("Current environment")));
  }
}
