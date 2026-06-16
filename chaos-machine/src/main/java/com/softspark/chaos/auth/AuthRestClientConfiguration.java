package com.softspark.chaos.auth;

import com.softspark.chaos.config.LoggingClientHttpRequestInterceptor;
import java.net.http.HttpClient;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Creates the {@link RestClient} bean dedicated to the external AUTH SERVICE.
 *
 * <p>Separate from the ledger {@link RestClient} so timeouts and base-URLs are independently
 * configurable. Emits a loud startup warning when running in permissive dev mode.
 */
@Configuration
@EnableConfigurationProperties(AuthProperties.class)
public class AuthRestClientConfiguration {

  private static final Logger log = LoggerFactory.getLogger(AuthRestClientConfiguration.class);

  /**
   * Creates the auth-service {@link RestClient} bean.
   *
   * @param authProperties bound auth-service configuration
   * @return the configured {@link RestClient}
   */
  @Bean
  @Qualifier("authRestClient")
  public RestClient authRestClient(AuthProperties authProperties) {
    if (!authProperties.clientAuth().enabled()) {
      log.warn(
          "auth-service.client-auth.enabled=false — permissive dev mode is ACTIVE. "
              + "Do NOT use in staging or production.");
    }

    var baseUrl =
        authProperties.baseUrl() != null && !authProperties.baseUrl().isBlank()
            ? authProperties.baseUrl()
            : "http://localhost";

    var jdkHttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    var factory = new JdkClientHttpRequestFactory(jdkHttpClient);
    factory.setReadTimeout(Duration.ofSeconds(5));

    return RestClient.builder()
        .baseUrl(baseUrl)
        .requestFactory(factory)
        .requestInterceptor(new LoggingClientHttpRequestInterceptor("auth"))
        .build();
  }
}
