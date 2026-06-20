package com.softspark.chaos.organization.seed;

import io.soabase.recordbuilder.core.RecordBuilder;

/**
 * Summary of a reference-data seed/refresh run.
 *
 * @param fetched              number of countries returned by the API
 * @param currenciesUpserted   number of currencies newly inserted
 * @param countriesUpserted    number of countries newly inserted
 * @param skipped              whether seeding was skipped (policy/empty checks)
 * @param error                an error message if the fetch failed (null on success)
 */
@RecordBuilder
public record SeedSummary(
    int fetched,
    int currenciesUpserted,
    int countriesUpserted,
    boolean skipped,
    String error) {

  /** A summary representing a skipped run. */
  public static SeedSummary ofSkipped() {
    return new SeedSummary(0, 0, 0, true, null);
  }

  /** A summary representing a failed fetch. */
  public static SeedSummary ofFailed(String error) {
    return new SeedSummary(0, 0, 0, false, error);
  }
}
