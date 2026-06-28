package com.softspark.chaos.dlq.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link DlqDomain} original-topic → domain derivation (ADR-029). */
@DisplayName("DlqDomain")
class DlqDomainTest {

  @Test
  @DisplayName("maps each topic family to its domain, most-specific first")
  void mapping() {
    assertThat(DlqDomain.of("collection.completed")).isEqualTo("COLLECTION");
    assertThat(DlqDomain.of("disbursement.initiated")).isEqualTo("DISBURSEMENT");
    assertThat(DlqDomain.of("disbursement.completed")).isEqualTo("DISBURSEMENT");
    assertThat(DlqDomain.of("disbursement.batch.initiated")).isEqualTo("BATCH_DISBURSEMENT");
    assertThat(DlqDomain.of("disbursement.batch.item.failed")).isEqualTo("BATCH_DISBURSEMENT");
    assertThat(DlqDomain.of("organization.va.settlement.failed")).isEqualTo("SETTLEMENT");
    assertThat(DlqDomain.of("organization.treasury.sweep.completed")).isEqualTo("TREASURY");
    assertThat(DlqDomain.of("organization.onboarded")).isEqualTo("ORGANIZATION");
    assertThat(DlqDomain.of("organization.va.updated")).isEqualTo("ORGANIZATION");
  }

  @Test
  @DisplayName("unknown/blank/null topics map to UNKNOWN")
  void unknown() {
    assertThat(DlqDomain.of(null)).isEqualTo("UNKNOWN");
    assertThat(DlqDomain.of("")).isEqualTo("UNKNOWN");
    assertThat(DlqDomain.of("weird.topic")).isEqualTo("UNKNOWN");
  }
}
