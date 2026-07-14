package com.softspark.chaos.ledgerproxy;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration for the statement-artifact download proxy (ADR-034).
 *
 * <p>Bound from {@code chaos.statements.*}. These govern the chaos machine's <em>own</em> hop to the
 * ledger's object store — a different animal from the sub-second JSON reads the ledger timeouts are
 * sized for, which is why an artifact transfer gets its own budget rather than borrowing
 * {@code ledger.timeouts.*}.
 *
 * @param artifact timeouts for the server-side fetch of the artifact from the object store
 * @param maxArtifactBytes the largest artifact the gateway will relay (default 50 MiB); a larger one
 *     is refused rather than buffered, so a pathological ledger artifact cannot exhaust the harness
 */
@ConfigurationProperties(prefix = "chaos.statements")
public record StatementProperties(
    @DefaultValue Artifact artifact, @DefaultValue("52428800") long maxArtifactBytes) {

  /**
   * Timeouts for the object-store hop.
   *
   * @param connectMs connect timeout in milliseconds (default 5s)
   * @param readMs read timeout in milliseconds (default 60s) — an artifact transfer is allowed to
   *     take far longer than a JSON read
   */
  public record Artifact(
      @DefaultValue("5000") long connectMs, @DefaultValue("60000") long readMs) {}
}
