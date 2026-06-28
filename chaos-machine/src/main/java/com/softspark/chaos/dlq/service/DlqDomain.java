package com.softspark.chaos.dlq.service;

/**
 * Derives the coarse DLQ <em>domain</em> bucket from a dead letter's original topic (ADR-029) — the
 * value the "dlt domain" filter offers. Order matters: more specific prefixes are checked first.
 */
public final class DlqDomain {

  /** Domain used when the original topic does not match any known prefix. */
  public static final String UNKNOWN = "UNKNOWN";

  private DlqDomain() {}

  /**
   * Maps an original inbound topic (e.g. {@code collection.completed},
   * {@code disbursement.batch.item.failed}, {@code organization.va.settlement.failed}) to its domain.
   *
   * @param originalTopic the original topic (nullable)
   * @return one of COLLECTION, BATCH_DISBURSEMENT, DISBURSEMENT, SETTLEMENT, TREASURY, ORGANIZATION,
   *     or UNKNOWN
   */
  public static String of(String originalTopic) {
    if (originalTopic == null || originalTopic.isBlank()) {
      return UNKNOWN;
    }
    String topic = originalTopic.trim().toLowerCase();
    if (topic.startsWith("collection")) {
      return "COLLECTION";
    }
    if (topic.startsWith("disbursement.batch")) {
      return "BATCH_DISBURSEMENT";
    }
    if (topic.startsWith("disbursement")) {
      return "DISBURSEMENT";
    }
    if (topic.startsWith("organization.va.settlement")) {
      return "SETTLEMENT";
    }
    if (topic.startsWith("organization.treasury")) {
      return "TREASURY";
    }
    if (topic.startsWith("organization")) {
      return "ORGANIZATION";
    }
    return UNKNOWN;
  }
}
