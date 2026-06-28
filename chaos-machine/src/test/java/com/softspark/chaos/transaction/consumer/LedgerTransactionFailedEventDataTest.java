package com.softspark.chaos.transaction.consumer;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.softspark.chaos.kafka.EventEnvelope;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Contract test pinning the ledger's exact snake_case {@code ledger.transaction.failed} JSON to the
 * {@link LedgerTransactionFailedEventData} mirror.
 */
@DisplayName("LedgerTransactionFailedEventData contract")
class LedgerTransactionFailedEventDataTest {

  private final ObjectMapper mapper =
      new ObjectMapper().registerModule(new JavaTimeModule());

  @Test
  @DisplayName("deserializes the ledger snake_case data payload")
  void deserializesData() throws Exception {
    String json =
        """
        {
          "transaction_id": "11111111-1111-1111-1111-111111111111",
          "transaction_request_id": "REQ-1",
          "transaction_type": "DISBURSEMENT",
          "failure_code": "INSUFFICIENT_FUNDS",
          "failure_reason": "Available balance too low"
        }
        """;

    var data = mapper.readValue(json, LedgerTransactionFailedEventData.class);

    assertThat(data.transactionId())
        .isEqualTo(UUID.fromString("11111111-1111-1111-1111-111111111111"));
    assertThat(data.transactionRequestId()).isEqualTo("REQ-1");
    assertThat(data.transactionType()).isEqualTo("DISBURSEMENT");
    assertThat(data.failureCode()).isEqualTo("INSUFFICIENT_FUNDS");
    assertThat(data.failureReason()).isEqualTo("Available balance too low");
  }

  @Test
  @DisplayName("deserializes inside the full event envelope")
  void deserializesEnvelope() throws Exception {
    String json =
        """
        {
          "event_id": "evt-1",
          "event_type": "ledger.transaction.failed",
          "timestamp": "2026-06-27T10:00:00Z",
          "source": "ledger-service",
          "version": "1.0",
          "data": {
            "transaction_id": "11111111-1111-1111-1111-111111111111",
            "transaction_request_id": "REQ-1",
            "transaction_type": "COLLECTION",
            "failure_code": "VALIDATION",
            "failure_reason": "bad"
          },
          "metadata": {
            "correlation_id": "rec-corr",
            "idempotency_key": "REQ-1:failed",
            "tenant_id": "tenant-1"
          }
        }
        """;

    EventEnvelope<LedgerTransactionFailedEventData> envelope =
        mapper.readValue(json, new TypeReference<>() {});

    assertThat(envelope.eventId()).isEqualTo("evt-1");
    assertThat(envelope.data().transactionRequestId()).isEqualTo("REQ-1");
    assertThat(envelope.metadata().idempotencyKey()).isEqualTo("REQ-1:failed");
  }
}
