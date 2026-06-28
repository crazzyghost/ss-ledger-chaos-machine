package com.softspark.chaos.history.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.softspark.chaos.flow.FlowBuilderRegistry;
import com.softspark.chaos.flow.FlowRequest;
import com.softspark.chaos.flow.FlowRequestBuilder;
import com.softspark.chaos.flow.model.FlowType;
import com.softspark.chaos.history.repository.PublishRecordRepository;
import com.softspark.chaos.kafka.ChaosEventPublisher;
import com.softspark.chaos.kafka.EventEnvelope;
import com.softspark.chaos.kafka.EventMetadata;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link AsyncHistoryWriter}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AsyncHistoryWriter")
class AsyncHistoryWriterTest {

  @Mock private PublishRecordRepository repository;
  @Mock private FlowBuilderRegistry builderRegistry;

  private AsyncHistoryWriter writer;

  @BeforeEach
  void setUp() {
    ObjectMapper mapper = new ObjectMapper();
    mapper.registerModule(new JavaTimeModule());
    mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    writer = new AsyncHistoryWriter(100, repository, mapper, builderRegistry);
    writer.startDrainThread();
  }

  private EventEnvelope<Object> envelope(String eventId) {
    var meta = new EventMetadata("corr-1", "idem-1", "tenant-1");
    return new EventEnvelope<>(
        eventId, "collection.completed", Instant.now(), "payment-service", "1.0", Map.of(), meta);
  }

  private FlowRequest request() {
    return FlowRequestBuilder.builder()
        .flowType(FlowType.COLLECTION_COMPLETED)
        .slotOverrides(Map.of())
        .flowFields(Map.of())
        .build();
  }

  @Nested
  @DisplayName("record")
  class RecordTests {

    @Test
    @DisplayName("returns a non-null historyId immediately")
    void returnsNonNullHistoryId() {
      var publishResult = new ChaosEventPublisher.PublishResult(100L, 0);
      String historyId =
          writer.record(envelope("EVT-001"), "topic", "key", publishResult, request(), null, false);
      assertThat(historyId).isNotBlank();
    }

    @Test
    @DisplayName("concurrent enqueues from multiple threads all return unique ids")
    void concurrentEnqueuesReturnUniqueIds() throws InterruptedException {
      int threadCount = 20;
      var latch = new CountDownLatch(threadCount);
      var historyIds = java.util.Collections.synchronizedList(new ArrayList<String>());

      for (int i = 0; i < threadCount; i++) {
        final int index = i;
        Thread.ofVirtual()
            .start(
                () -> {
                  var publishResult = new ChaosEventPublisher.PublishResult(index, 0);
                  String id =
                      writer.record(
                          envelope("EVT-" + index),
                          "topic",
                          "key",
                          publishResult,
                          request(),
                          null,
                          false);
                  historyIds.add(id);
                  latch.countDown();
                });
      }

      assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
      assertThat(historyIds).hasSize(threadCount);
      assertThat(historyIds).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("eventually persists enqueued events via repository")
    void eventuallyPersistsEvents() throws InterruptedException {
      var publishResult = new ChaosEventPublisher.PublishResult(1L, 0);
      writer.record(envelope("EVT-PERSIST"), "topic", "key", publishResult, request(), null, false);

      // Give the drain thread time to process
      Thread.sleep(200);

      verify(repository, atLeastOnce()).save(any());
    }
  }

  @Nested
  @DisplayName("queue overflow")
  class QueueOverflow {

    @Test
    @DisplayName("drops events when queue is full without blocking")
    void dropsEventsWhenQueueFull() {
      // Fill a tiny-capacity writer
      ObjectMapper mapper = new ObjectMapper();
      mapper.registerModule(new JavaTimeModule());
      mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
      var tinyWriter = new AsyncHistoryWriter(2, repository, mapper, builderRegistry);
      // Do NOT start drain thread so queue fills up

      var publishResult = new ChaosEventPublisher.PublishResult(1L, 0);
      List<String> ids = new ArrayList<>();

      // Enqueue more than capacity — should not throw or block
      for (int i = 0; i < 10; i++) {
        String id =
            tinyWriter.record(
                envelope("EVT-" + i), "topic", "key", publishResult, request(), null, false);
        ids.add(id);
      }

      // All calls returned ids (pre-generated before enqueue)
      assertThat(ids).hasSize(10);
      assertThat(ids).doesNotHaveDuplicates();
    }
  }
}
