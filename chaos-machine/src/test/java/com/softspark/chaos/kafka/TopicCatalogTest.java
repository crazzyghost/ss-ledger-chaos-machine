package com.softspark.chaos.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import com.softspark.chaos.flow.model.FlowType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link TopicCatalog} topic resolution and DLT derivation (ADR-024). */
@DisplayName("TopicCatalog")
class TopicCatalogTest {

  private final TopicCatalog catalog = new TopicCatalog();

  @Nested
  @DisplayName("ledger-outbound topic defaults")
  class LedgerOutboundDefaults {

    @Test
    @DisplayName("transaction-failed defaults to ledger.transaction.failed")
    void transactionFailedDefault() {
      assertThat(catalog.getLedgerTransactionFailed()).isEqualTo("ledger.transaction.failed");
    }

    @Test
    @DisplayName("balance-updated defaults to ledger.balance.updated")
    void balanceUpdatedDefault() {
      assertThat(catalog.getLedgerBalanceUpdated()).isEqualTo("ledger.balance.updated");
    }

    @Test
    @DisplayName("reservation created/released default to ledger.reservation.{created,released}")
    void reservationDefaults() {
      assertThat(catalog.getLedgerReservationCreated()).isEqualTo("ledger.reservation.created");
      assertThat(catalog.getLedgerReservationReleased()).isEqualTo("ledger.reservation.released");
    }

    @Test
    @DisplayName("account-created is unchanged")
    void accountCreatedDefault() {
      assertThat(catalog.getLedgerAccountCreated()).isEqualTo("ledger.account.created");
    }

    @Test
    @DisplayName("overriding the property is reflected by the getter")
    void overrideTopic() {
      catalog.setLedgerTransactionFailed("custom.failed");
      assertThat(catalog.getLedgerTransactionFailed()).isEqualTo("custom.failed");
    }
  }

  @Nested
  @DisplayName("dltFor")
  class DltFor {

    @Test
    @DisplayName("appends .dlt to a topic")
    void appendsDlt() {
      assertThat(TopicCatalog.dltFor("ledger.transaction.failed"))
          .isEqualTo("ledger.transaction.failed.dlt");
      assertThat(TopicCatalog.dltFor("ledger.account.created"))
          .isEqualTo("ledger.account.created.dlt");
    }
  }

  @Test
  @DisplayName("topicFor resolves an outbound flow type unchanged")
  void topicForUnchanged() {
    assertThat(catalog.topicFor(FlowType.COLLECTION_COMPLETED)).isEqualTo("collection.completed");
  }
}
