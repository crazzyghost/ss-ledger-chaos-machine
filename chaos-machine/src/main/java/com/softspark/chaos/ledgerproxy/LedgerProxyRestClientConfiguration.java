package com.softspark.chaos.ledgerproxy;

import com.softspark.chaos.config.LedgerProperties;
import com.softspark.chaos.config.LoggingClientHttpRequestInterceptor;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Creates the {@link RestClient} bean dedicated to the ledger read proxy.
 *
 * <p>This is intentionally separate from the provisioning client ({@code ledgerRestClient}) so
 * that the proxy can forward per-request bearer tokens without a static auth header.
 */
@Configuration
@EnableConfigurationProperties(LedgerProxyProperties.class)
public class LedgerProxyRestClientConfiguration {

  /**
   * Creates the ledger proxy {@link RestClient} bean.
   *
   * <p>No default {@code Authorization} header is set here; the {@link LedgerClient} attaches
   * the caller's token (or a configured service token) on each individual request.
   *
   * @param ledgerProperties shared ledger connection settings
   * @return the configured {@link RestClient}
   */
  @Bean
  @Qualifier("ledgerProxyRestClient")
  public RestClient ledgerProxyRestClient(LedgerProperties ledgerProperties) {
    var jdkHttpClient =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(ledgerProperties.timeouts().connectMs()))
            .build();
    var factory = new JdkClientHttpRequestFactory(jdkHttpClient);
    factory.setReadTimeout(Duration.ofMillis(ledgerProperties.timeouts().readMs()));
    return RestClient.builder()
        .baseUrl(ledgerProperties.baseUrl())
        .requestFactory(factory)
        .requestInterceptor(new LoggingClientHttpRequestInterceptor("ledger-proxy"))
        .build();
  }
}
