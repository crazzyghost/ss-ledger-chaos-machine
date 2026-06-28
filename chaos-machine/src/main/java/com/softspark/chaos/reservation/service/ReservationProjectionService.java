package com.softspark.chaos.reservation.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.softspark.chaos.account.model.VirtualAccount;
import com.softspark.chaos.account.repository.VirtualAccountRepository;
import com.softspark.chaos.base.Ids;
import com.softspark.chaos.kafka.EventEnvelope;
import com.softspark.chaos.kafka.EventMetadata;
import com.softspark.chaos.reservation.consumer.LedgerReservationLifecycleEventData;
import com.softspark.chaos.reservation.model.Reservation;
import com.softspark.chaos.reservation.repository.ReservationRepository;
import java.time.Instant;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Upserts a reservation lifecycle event ({@code created} or {@code released}) into the stateful
 * {@code reservation} projection (ADR-028).
 *
 * <p>The state machine is <strong>monotonic</strong>: status advances by a fixed ordinal
 * ({@code ACTIVE} &lt; {@code PARTIALLY_RESOLVED} &lt; terminal) and never regresses; a terminal
 * status is sticky. A {@code created} arriving after a {@code released} (reorder) fills the base
 * fields without regressing status. Immediate redelivery is deduped by {@code last_event_id}; the
 * release count is best-effort.
 */
@Service
public class ReservationProjectionService {

  private static final Logger log = LoggerFactory.getLogger(ReservationProjectionService.class);

  private static final String ACTIVE = "ACTIVE";
  private static final Set<String> TERMINAL_STATUSES = Set.of("CAPTURED", "RELEASED", "EXPIRED");

  private final ReservationRepository repository;
  private final VirtualAccountRepository virtualAccountRepository;
  private final ObjectMapper kafkaObjectMapper;

  public ReservationProjectionService(
      ReservationRepository repository,
      VirtualAccountRepository virtualAccountRepository,
      @Qualifier("kafkaObjectMapper") ObjectMapper kafkaObjectMapper) {
    this.repository = repository;
    this.virtualAccountRepository = virtualAccountRepository;
    this.kafkaObjectMapper = kafkaObjectMapper;
  }

  /**
   * Applies one reservation lifecycle envelope. Safe to call repeatedly with the same event.
   *
   * @param envelope the consumed envelope (may be null/partial)
   */
  @Transactional
  public void project(EventEnvelope<LedgerReservationLifecycleEventData> envelope) {
    if (envelope == null
        || envelope.data() == null
        || envelope.data().reservationId() == null
        || envelope.eventId() == null) {
      log.warn("Skipping reservation event with empty envelope/data/reservationId/event_id");
      return;
    }

    LedgerReservationLifecycleEventData data = envelope.data();
    EventMetadata metadata = envelope.metadata();
    String eventId = envelope.eventId();
    String reservationId = data.reservationId().toString();
    String accountId = data.accountId() == null ? null : data.accountId().toString();
    boolean created = ACTIVE.equalsIgnoreCase(data.status());
    Instant timestamp = envelope.timestamp();

    try {
      Reservation existing = repository.findById(reservationId).orElse(null);
      if (existing == null) {
        repository.save(
            insert(reservationId, accountId, data, metadata, eventId, created, timestamp, envelope));
        log.debug(
            "Projected reservation {} created={} status={}", reservationId, created, data.status());
        return;
      }

      // Immediate redelivery of the last applied event — no-op.
      if (eventId.equals(existing.getLastEventId())) {
        log.debug("Duplicate reservation event {} for {} — skipping", eventId, reservationId);
        return;
      }

      apply(existing, accountId, data, eventId, created, timestamp, envelope);
      repository.save(existing);
      log.debug(
          "Updated reservation {} status={} releases={}",
          reservationId,
          existing.getStatus(),
          existing.getReleaseEventCount());
    } catch (org.springframework.dao.DataIntegrityViolationException e) {
      // A concurrent redelivery won the primary-key (reservation_id) race — already projected.
      log.debug("Concurrent duplicate reservation {} — already projected", reservationId);
    }
  }

  private Reservation insert(
      String reservationId,
      String accountId,
      LedgerReservationLifecycleEventData data,
      EventMetadata metadata,
      String eventId,
      boolean created,
      Instant timestamp,
      EventEnvelope<LedgerReservationLifecycleEventData> envelope) {
    Reservation r = new Reservation();
    r.setReservationId(reservationId);
    r.setAccountId(accountId);
    r.setTransactionId(data.transactionId());
    r.setReservationType(data.reservationType());
    r.setDisbursementBatchId(data.disbursementBatchId());
    r.setAmount(data.amount());
    r.setCurrency(lookupCurrency(accountId));
    r.setStatus(data.status());
    r.setCreatedEventId(created ? eventId : null);
    r.setLastEventId(eventId);
    r.setReleaseEventCount(created ? 0 : 1);
    r.setTenantId(metadata == null ? null : metadata.tenantId());
    r.setCreatedAt(created ? timestamp : null);
    r.setUpdatedAt(timestamp);
    r.setTerminalAt(isTerminal(data.status()) ? timestamp : null);
    r.setPayloadJson(serialize(envelope));
    return r;
  }

  private void apply(
      Reservation r,
      String accountId,
      LedgerReservationLifecycleEventData data,
      String eventId,
      boolean created,
      Instant timestamp,
      EventEnvelope<LedgerReservationLifecycleEventData> envelope) {
    if (created) {
      // A late 'created' (reorder): fill base fields without regressing status.
      if (r.getCreatedEventId() == null) {
        r.setCreatedEventId(eventId);
      }
      if (r.getCreatedAt() == null) {
        r.setCreatedAt(timestamp);
      }
      if (r.getReservationType() == null) {
        r.setReservationType(data.reservationType());
      }
      if (r.getDisbursementBatchId() == null) {
        r.setDisbursementBatchId(data.disbursementBatchId());
      }
      if (r.getAmount() == null) {
        r.setAmount(data.amount());
      }
      if (r.getCurrency() == null) {
        r.setCurrency(lookupCurrency(accountId));
      }
    } else {
      r.setReleaseEventCount(r.getReleaseEventCount() + 1);
    }

    // Advance status monotonically; set terminal_at on first terminal.
    if (rank(data.status()) > rank(r.getStatus())) {
      r.setStatus(data.status());
      if (isTerminal(data.status()) && r.getTerminalAt() == null) {
        r.setTerminalAt(timestamp);
      }
    }

    r.setLastEventId(eventId);
    r.setUpdatedAt(timestamp);
    r.setPayloadJson(serialize(envelope));
  }

  private static int rank(String status) {
    if (status == null) {
      return 0;
    }
    return switch (status.toUpperCase()) {
      case ACTIVE -> 1;
      case "PARTIALLY_RESOLVED" -> 2;
      case "CAPTURED", "RELEASED", "EXPIRED" -> 3;
      default -> 0;
    };
  }

  private static boolean isTerminal(String status) {
    return status != null && TERMINAL_STATUSES.contains(status.toUpperCase());
  }

  private String lookupCurrency(String accountId) {
    if (accountId == null) {
      return null;
    }
    return virtualAccountRepository
        .findById(accountId)
        .map(VirtualAccount::getCurrency)
        .orElse(null);
  }

  private String serialize(EventEnvelope<LedgerReservationLifecycleEventData> envelope) {
    try {
      return kafkaObjectMapper.writeValueAsString(envelope);
    } catch (JsonProcessingException e) {
      log.warn("Failed to serialize reservation payload for event {}", envelope.eventId());
      return null;
    }
  }
}
