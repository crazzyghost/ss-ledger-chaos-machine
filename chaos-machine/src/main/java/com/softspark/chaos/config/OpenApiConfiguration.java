package com.softspark.chaos.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI configuration for the chaos machine.
 * <p>
 * Configures Swagger UI and API documentation.
 */
@Configuration
public class OpenApiConfiguration {

    @Bean
    public OpenAPI chaosOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Ledger Chaos Machine API")
                        .description("API for publishing controlled event sequences to test ledger behavior")
                        .version("v0"))
                .servers(List.of(
                        new Server().url("http://localhost:27100").description("Local development"),
                        new Server().url("/").description("Current environment")
                ));
    }
}
