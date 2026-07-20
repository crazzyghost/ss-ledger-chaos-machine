package com.softspark.chaos.transaction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.softspark.chaos.kafka.EventEnvelope;
import com.softspark.chaos.kafka.EventMetadata;
import com.softspark.chaos.transaction.consumer.LedgerTransactionFailedEventData;
import com.softspark.chaos.transaction.model.TransactionFailure;
import com.softspark.chaos.transaction.repository.TransactionFailureRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit tests for {@link TransactionFailureProjectionService}. */
@ExtendWith(MockitoExtension.class)
@DisplayName("TransactionFailureProjectionService")
class TransactionFailureProjectionServiceTest {

  private static final UUID LEDGER_TXN_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

  @Mock private TransactionFailureRepository repository;

  private TransactionFailureProjectionService service;

  @BeforeEach
  void setUp() {
    ObjectMapper mapper = new ObjectMapper();
    mapper.registerModule(new JavaTimeModule());
    mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    service = new TransactionFailureProjectionService(repository, mapper);
  }

  private EventEnvelope<LedgerTransactionFailedEventData> envelope(String eventId) {
    var data =
        new LedgerTransactionFailedEventData(
            LEDGER_TXN_ID, "REQ-1", "COLLECTION", "INSUFFICIENT_FUNDS", "Not enough funds");
    var meta = new EventMetadata("rec-corr", "REQ-1:failed", "tenant-1");
    return new EventEnvelope<>(
        eventId,
        "ledger.transaction.failed",
        Instant.parse("2026-06-27T10:00:00Z"),
        "ledger-service",
        "1.0",
        data,
        meta);
  }

  @Test
  @DisplayName("maps every field, keeping the two ids distinct")
  void mapsAllFields() {
    when(repository.existsByEventId("evt-1")).thenReturn(false);

    service.project(envelope("evt-1"));

    ArgumentCaptor<TransactionFailure> captor = ArgumentCaptor.forClass(TransactionFailure.class);
    verify(repository).save(captor.capture());
    TransactionFailure saved = captor.getValue();
    assertThat(saved.getId()).isNotBlank();
    assertThat(saved.getEventId()).isEqualTo("evt-1");
    assertThat(saved.getTransactionRequestId()).isEqualTo("REQ-1");
    assertThat(saved.getLedgerTransactionId()).isEqualTo(LEDGER_TXN_ID.toString());
    assertThat(saved.getTransactionType()).isEqualTo("COLLECTION");
    assertThat(saved.getFailureCode()).isEqualTo("INSUFFICIENT_FUNDS");
    assertThat(saved.getFailureReason()).isEqualTo("Not enough funds");
    assertThat(saved.getLedgerCorrelationId()).isEqualTo("rec-corr");
    assertThat(saved.getIdempotencyKey()).isEqualTo("REQ-1:failed");
    assertThat(saved.getTenantId()).isEqualTo("tenant-1");
    assertThat(saved.getOccurredAt()).isEqualTo(Instant.parse("2026-06-27T10:00:00Z"));
    assertThat(saved.getReceivedAt()).isNotNull();
    assertThat(saved.getPayloadJson()).contains("transaction_request_id");
  }

  @Test
  @DisplayName("redelivery (existing event_id) is a no-op")
  void idempotentByEventId() {
    when(repository.existsByEventId("evt-1")).thenReturn(true);

    service.project(envelope("evt-1"));

    verify(repository, never()).save(any());
  }

  @Test
  @DisplayName("null data is skipped without saving or DLT")
  void nullDataSkipped() {
    var empty =
        new EventEnvelope<LedgerTransactionFailedEventData>(
            "evt-x",
            "ledger.transaction.failed",
            Instant.now(),
            "ledger-service",
            "1.0",
            null,
            null);

    service.project(empty);

    verify(repository, never()).save(any());
  }
}
