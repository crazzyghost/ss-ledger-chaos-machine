package com.softspark.chaos.config;

import com.softspark.chaos.flow.chaos.ChaosLimits;
import com.softspark.chaos.ledgerproxy.StatementProperties;
import com.softspark.chaos.organization.outbox.OutboxProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Registers immutable {@link org.springframework.boot.context.properties.ConfigurationProperties}
 * records that cannot use {@code @Component} (because records require constructor binding, not
 * setter injection).
 */
@Configuration
@EnableConfigurationProperties({
  ChaosLimits.class,
  OutboxProperties.class,
  StatementProperties.class
})
public class ChaosPropertiesConfiguration {}
