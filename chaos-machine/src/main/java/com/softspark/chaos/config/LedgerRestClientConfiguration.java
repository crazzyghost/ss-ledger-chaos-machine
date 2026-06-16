package com.softspark.chaos.config;

import com.softspark.chaos.account.bootstrap.LedgerProvisioningException;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Configures a dedicated {@link RestClient} for communicating with the ledger service.
 *
 * <p>The client is backed by the JDK's built-in {@link HttpClient} (Java 11+) to avoid adding
 * reactive Netty dependencies to this Spring MVC application. A {@code defaultStatusHandler} for
 * 5xx responses automatically converts server errors into {@link LedgerProvisioningException} so
 * that the provisioning client can apply its retry policy.
 */
@Configuration
@EnableConfigurationProperties(LedgerProperties.class)
public class LedgerRestClientConfiguration {

  /**
   * Creates and configures the ledger-dedicated {@link RestClient} bean.
   *
   * <p>Connection timeout is enforced by the underlying JDK {@link HttpClient}; read timeout is
   * set on the {@link JdkClientHttpRequestFactory}. The default status handler for 5xx responses
   * throws {@link LedgerProvisioningException} so callers can implement retries.
   *
   * @param ledgerProperties bound configuration properties for the ledger client
   * @return the configured {@link RestClient}
   */
  @Bean
  @Qualifier("ledgerRestClient")
  public RestClient ledgerRestClient(LedgerProperties ledgerProperties) {
    var jdkHttpClient =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(ledgerProperties.timeouts().connectMs()))
            .build();

    var factory = new JdkClientHttpRequestFactory(jdkHttpClient);
    factory.setReadTimeout(Duration.ofMillis(ledgerProperties.timeouts().readMs()));

    return RestClient.builder()
        .baseUrl(ledgerProperties.baseUrl())
        .defaultHeader("Authorization", "Bearer %s".formatted(ledgerProperties.authToken()))
        .requestFactory(factory)
        .requestInterceptor(new LoggingClientHttpRequestInterceptor("ledger-provisioning"))
        .defaultStatusHandler(
            HttpStatusCode::is5xxServerError,
            (request, response) -> {
              throw new LedgerProvisioningException(
                  "Ledger server error: " + response.getStatusCode().value(),
                  response.getStatusCode().value());
            })
        .build();
  }
}
