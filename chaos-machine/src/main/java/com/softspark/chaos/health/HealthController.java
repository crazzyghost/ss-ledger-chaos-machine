package com.softspark.chaos.health;

import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for chaos machine status and health checks.
 */
@RestController
@RequestMapping("/api/v0/health")
public class HealthController {

  @Value("${chaos.kafka.cluster-label}")
  private String clusterLabel;

  /**
   * Returns the health status of the chaos machine.
   *
   * @return health response with current status and cluster label
   */
  @GetMapping
  public HealthResponse getHealth() {
    return new HealthResponse("UP", Instant.now(), clusterLabel);
  }
}
