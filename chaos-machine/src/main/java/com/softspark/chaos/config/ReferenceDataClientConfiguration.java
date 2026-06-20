package com.softspark.chaos.config;

import com.softspark.chaos.organization.seed.ReferenceDataProperties;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Configures a dedicated {@link RestClient} for the restcountries.com reference-data seeder.
 *
 * <p>Kept separate from the ledger proxy client: it targets a public third-party API, carries no
 * authorization header, and applies its own bounded connect/read timeouts from
 * {@link ReferenceDataProperties}. Backed by the JDK {@link HttpClient} to avoid reactive Netty
 * dependencies.
 */
@Configuration
@EnableConfigurationProperties(ReferenceDataProperties.class)
public class ReferenceDataClientConfiguration {

  /**
   * Creates the restcountries.com-dedicated {@link RestClient} bean.
   *
   * @param properties bound reference-data configuration
   * @return the configured {@link RestClient}
   */
  @Bean
  @Qualifier("restCountriesRestClient")
  public RestClient restCountriesRestClient(ReferenceDataProperties properties) {
    var rc = properties.restcountries();

    var jdkHttpClient =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(rc.connectMs()))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    var factory = new JdkClientHttpRequestFactory(jdkHttpClient);
    factory.setReadTimeout(Duration.ofMillis(rc.readMs()));

    return RestClient.builder().baseUrl(rc.baseUrl()).requestFactory(factory).build();
  }
}
