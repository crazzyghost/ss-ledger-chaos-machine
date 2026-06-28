package com.softspark.chaos.reservation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.softspark.chaos.account.repository.VirtualAccountRepository;
import com.softspark.chaos.kafka.EventEnvelope;
import com.softspark.chaos.kafka.EventMetadata;
import com.softspark.chaos.reservation.consumer.LedgerReservationLifecycleEventData;
import com.softspark.chaos.reservation.model.Reservation;
import com.softspark.chaos.reservation.repository.ReservationRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit tests for the {@link ReservationProjectionService} monotonic state machine. */
@ExtendWith(MockitoExtension.class)
@DisplayName("ReservationProjectionService")
class ReservationProjectionServiceTest {

  private static final String RESV = "33333333-3333-3333-3333-333333333333";
  private static final UUID ACCT = UUID.fromString("44444444-4444-4444-4444-444444444444");

  @Mock private ReservationRepository repository;
  @Mock private VirtualAccountRepository virtualAccountRepository;

  private Map<String, Reservation> store;
  private ReservationProjectionService service;

  @BeforeEach
  void setUp() {
    store = new HashMap<>();
    lenient()
        .when(repository.findById(anyString()))
        .thenAnswer(inv -> Optional.ofNullable(store.get(inv.<String>getArgument(0))));
    lenient()
        .when(repository.save(any(Reservation.class)))
        .thenAnswer(
            inv -> {
              Reservation r = inv.getArgument(0);
              store.put(r.getReservationId(), r);
              return r;
            });
    lenient().when(virtualAccountRepository.findById(anyString())).thenReturn(Optional.empty());

    ObjectMapper mapper = new ObjectMapper();
    mapper.registerModule(new JavaTimeModule());
    mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    service = new ReservationProjectionService(repository, virtualAccountRepository, mapper);
  }

  private EventEnvelope<LedgerReservationLifecycleEventData> event(
      String eventId, String status, String type, String batchId, String amount) {
    var data =
        new LedgerReservationLifecycleEventData(
            UUID.fromString(RESV), ACCT, "TXN-1", type, new BigDecimal(amount), status, batchId);
    var meta = new EventMetadata("corr", "key", "tenant-1");
    String topicSuffix = "ACTIVE".equals(status) ? "created" : "released";
    return new EventEnvelope<>(
        eventId,
        "ledger.reservation." + topicSuffix,
        Instant.now(),
        "ledger-service",
        "1.0",
        data,
        meta);
  }

  @Test
  @DisplayName("created inserts an ACTIVE row")
  void createdActive() {
    service.project(event("e1", "ACTIVE", "SINGLE", null, "50.00"));

    Reservation r = store.get(RESV);
    assertThat(r).isNotNull();
    assertThat(r.getStatus()).isEqualTo("ACTIVE");
    assertThat(r.getAmount()).isEqualByComparingTo("50.00");
    assertThat(r.getReleaseEventCount()).isZero();
    assertThat(r.getCreatedEventId()).isEqualTo("e1");
    assertThat(r.getCreatedAt()).isNotNull();
    assertThat(r.getTerminalAt()).isNull();
  }

  @Test
  @DisplayName("created then released(CAPTURED) advances to terminal and sets terminal_at")
  void createdThenCaptured() {
    service.project(event("e1", "ACTIVE", "SINGLE", null, "50.00"));
    service.project(event("e2", "CAPTURED", "SINGLE", null, "50.00"));

    Reservation r = store.get(RESV);
    assertThat(r.getStatus()).isEqualTo("CAPTURED");
    assertThat(r.getReleaseEventCount()).isEqualTo(1);
    assertThat(r.getTerminalAt()).isNotNull();
    assertThat(r.getLastEventId()).isEqualTo("e2");
  }

  @Test
  @DisplayName("batch created + N releases ends terminal with release count")
  void batchMultiRelease() {
    service.project(event("e1", "ACTIVE", "BATCH", "BATCH-9", "300.00"));
    service.project(event("e2", "PARTIALLY_RESOLVED", "BATCH", "BATCH-9", "300.00"));
    service.project(event("e3", "PARTIALLY_RESOLVED", "BATCH", "BATCH-9", "300.00"));
    service.project(event("e4", "CAPTURED", "BATCH", "BATCH-9", "300.00"));

    Reservation r = store.get(RESV);
    assertThat(r.getStatus()).isEqualTo("CAPTURED");
    assertThat(r.getReleaseEventCount()).isEqualTo(3);
    assertThat(r.getDisbursementBatchId()).isEqualTo("BATCH-9");
    assertThat(r.getReservationType()).isEqualTo("BATCH");
  }

  @Test
  @DisplayName("redelivery of the last event is a no-op (count unchanged)")
  void redeliveryNoOp() {
    service.project(event("e1", "ACTIVE", "SINGLE", null, "50.00"));
    service.project(event("e2", "RELEASED", "SINGLE", null, "50.00"));
    service.project(event("e2", "RELEASED", "SINGLE", null, "50.00")); // redelivery

    Reservation r = store.get(RESV);
    assertThat(r.getStatus()).isEqualTo("RELEASED");
    assertThat(r.getReleaseEventCount()).isEqualTo(1);
  }

  @Test
  @DisplayName("terminal status never regresses on a later out-of-order release")
  void terminalSticky() {
    service.project(event("e1", "ACTIVE", "SINGLE", null, "50.00"));
    service.project(event("e2", "CAPTURED", "SINGLE", null, "50.00"));
    service.project(event("e3", "PARTIALLY_RESOLVED", "SINGLE", null, "50.00")); // stale/reorder

    Reservation r = store.get(RESV);
    assertThat(r.getStatus()).isEqualTo("CAPTURED");
  }

  @Test
  @DisplayName("released arriving before created fills base fields without regressing status")
  void reorderReleasedThenCreated() {
    service.project(event("e2", "PARTIALLY_RESOLVED", "BATCH", "BATCH-9", "300.00"));
    service.project(event("e1", "ACTIVE", "BATCH", "BATCH-9", "300.00")); // late created

    Reservation r = store.get(RESV);
    assertThat(r.getStatus()).isEqualTo("PARTIALLY_RESOLVED"); // not regressed to ACTIVE
    assertThat(r.getCreatedEventId()).isEqualTo("e1");
    assertThat(r.getCreatedAt()).isNotNull();
  }
}
