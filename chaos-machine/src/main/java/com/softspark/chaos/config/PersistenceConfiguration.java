package com.softspark.chaos.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Persistence configuration for the chaos machine.
 * <p>
 * Provides a Clock bean for testable timestamp generation across the application.
 */
@Configuration
public class PersistenceConfiguration {

  /**
   * Provides a system UTC clock for timestamp generation.
   * <p>
   * This bean can be mocked in tests to control time-dependent behavior.
   *
   * @return a Clock instance using the system default zone
   */
  @Bean
  public Clock clock() {
    return Clock.systemUTC();
  }
}
