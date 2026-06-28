package com.softspark.chaos.dlq.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.softspark.chaos.dlq.consumer.LedgerDeadLetterRecord;
import com.softspark.chaos.dlq.model.DeadLetterRecord;
import com.softspark.chaos.dlq.repository.DeadLetterRecordRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit tests for {@link DeadLetterProjectionService}. */
@ExtendWith(MockitoExtension.class)
@DisplayName("DeadLetterProjectionService")
class DeadLetterProjectionServiceTest {

  @Mock private DeadLetterRecordRepository repository;

  private ObjectMapper mapper;
  private DeadLetterProjectionService service;

  @BeforeEach
  void setUp() {
    mapper = new ObjectMapper();
    mapper.registerModule(new JavaTimeModule());
    mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    service = new DeadLetterProjectionService(repository, mapper);
  }

  private JsonNode originalEvent() throws Exception {
    return mapper.readTree(
        """
        {
          "event_id": "orig-evt-1",
          "event_type": "collection.completed",
          "data": { "transaction_id": "TXN-7", "transaction_type": "COLLECTION", "amount": "100" }
        }
        """);
  }

  private LedgerDeadLetterRecord record(JsonNode original, String classification) {
    var failure =
        new LedgerDeadLetterRecord.Failure(
            classification, "java.lang.IllegalStateException", "boom", 3);
    return new LedgerDeadLetterRecord(
        UUID.fromString("55555555-5555-5555-5555-555555555555"),
        Instant.parse("2026-06-27T10:00:00Z"),
        "collection.completed",
        2,
        99L,
        "key-1",
        failure,
        original);
  }

  @Test
  @DisplayName("maps coords/domain/failure and best-effort txn from the original payload")
  void mapsRecord() throws Exception {
    when(repository.existsByDltTopicAndDltPartitionAndDltOffset(anyString(), anyInt(), anyLong()))
        .thenReturn(false);

    service.project(record(originalEvent(), "PROCESSING"), "ledger.collection.completed.dlt", 0, 5);

    ArgumentCaptor<DeadLetterRecord> captor = ArgumentCaptor.forClass(DeadLetterRecord.class);
    verify(repository).save(captor.capture());
    DeadLetterRecord saved = captor.getValue();
    assertThat(saved.getDltTopic()).isEqualTo("ledger.collection.completed.dlt");
    assertThat(saved.getDltPartition()).isZero();
    assertThat(saved.getDltOffset()).isEqualTo(5L);
    assertThat(saved.getOriginalTopic()).isEqualTo("collection.completed");
    assertThat(saved.getDomain()).isEqualTo("COLLECTION");
    assertThat(saved.getSource()).isEqualTo("LEDGER_INBOUND");
    assertThat(saved.getEventType()).isEqualTo("collection.completed");
    assertThat(saved.getEventId()).isEqualTo("orig-evt-1");
    assertThat(saved.getTransactionId()).isEqualTo("TXN-7");
    assertThat(saved.getTransactionType()).isEqualTo("COLLECTION");
    assertThat(saved.getFailureClassification()).isEqualTo("PROCESSING");
    assertThat(saved.getErrorType()).isEqualTo("java.lang.IllegalStateException");
    assertThat(saved.getErrorMessage()).isEqualTo("boom");
    assertThat(saved.getRetryCount()).isEqualTo(3);
    assertThat(saved.getOriginalPartition()).isEqualTo(2);
    assertThat(saved.getOriginalOffset()).isEqualTo(99L);
    assertThat(saved.getOriginalKey()).isEqualTo("key-1");
    assertThat(saved.getOriginalPayloadJson()).contains("transaction_id");
    assertThat(saved.getRawDltJson()).contains("original_topic");
    assertThat(saved.getReceivedAt()).isNotNull();
  }

  @Test
  @DisplayName("DESERIALIZATION dead letter (null originalEvent) stored with null txn/payload")
  void nullOriginalEvent() {
    when(repository.existsByDltTopicAndDltPartitionAndDltOffset(anyString(), anyInt(), anyLong()))
        .thenReturn(false);

    service.project(record(null, "DESERIALIZATION"), "ledger.collection.completed.dlt", 1, 7);

    ArgumentCaptor<DeadLetterRecord> captor = ArgumentCaptor.forClass(DeadLetterRecord.class);
    verify(repository).save(captor.capture());
    DeadLetterRecord saved = captor.getValue();
    // domain derives from the record's originalTopic (collection.completed), not the .dlt topic
    assertThat(saved.getDomain()).isEqualTo("COLLECTION");
    assertThat(saved.getFailureClassification()).isEqualTo("DESERIALIZATION");
    assertThat(saved.getTransactionId()).isNull();
    assertThat(saved.getEventType()).isNull();
    assertThat(saved.getOriginalPayloadJson()).isNull();
    assertThat(saved.getRawDltJson()).isNotNull();
  }

  @Test
  @DisplayName("redelivery (same coords) is a no-op")
  void dedupByCoords() throws Exception {
    when(repository.existsByDltTopicAndDltPartitionAndDltOffset(anyString(), anyInt(), anyLong()))
        .thenReturn(true);

    service.project(record(originalEvent(), "PROCESSING"), "ledger.collection.completed.dlt", 0, 5);

    verify(repository, never()).save(any());
  }
}
