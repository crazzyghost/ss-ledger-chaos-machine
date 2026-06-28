package com.softspark.chaos.balance.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.softspark.chaos.account.model.VirtualAccount;
import com.softspark.chaos.account.repository.VirtualAccountRepository;
import com.softspark.chaos.balance.consumer.LedgerBalanceUpdatedEventData;
import com.softspark.chaos.balance.model.BalanceHistory;
import com.softspark.chaos.balance.repository.BalanceHistoryRepository;
import com.softspark.chaos.base.Ids;
import com.softspark.chaos.kafka.EventEnvelope;
import com.softspark.chaos.kafka.EventMetadata;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Maps a {@code ledger.balance.updated} envelope into the {@code balance_history} projection
 * (ADR-027). Idempotent by envelope {@code event_id}; currency is backfilled best-effort from the VA
 * registry; a null/partial envelope is logged and skipped (never DLT).
 *
 * <p>Two <em>distinct</em> events for the same account in one transaction (e.g. a funding entry and
 * a reservation release) have distinct {@code event_id}s and are legitimately separate rows.
 */
@Service
public class BalanceHistoryProjectionService {

  private static final Logger log = LoggerFactory.getLogger(BalanceHistoryProjectionService.class);

  private final BalanceHistoryRepository repository;
  private final VirtualAccountRepository virtualAccountRepository;
  private final ObjectMapper kafkaObjectMapper;

  public BalanceHistoryProjectionService(
      BalanceHistoryRepository repository,
      VirtualAccountRepository virtualAccountRepository,
      @Qualifier("kafkaObjectMapper") ObjectMapper kafkaObjectMapper) {
    this.repository = repository;
    this.virtualAccountRepository = virtualAccountRepository;
    this.kafkaObjectMapper = kafkaObjectMapper;
  }

  /**
   * Projects a single balance-updated envelope. Safe to call repeatedly with the same event.
   *
   * @param envelope the consumed envelope (may be null/partial)
   */
  @Transactional
  public void project(EventEnvelope<LedgerBalanceUpdatedEventData> envelope) {
    if (envelope == null || envelope.data() == null || envelope.eventId() == null) {
      log.warn("Skipping ledger.balance.updated with empty envelope/data/event_id");
      return;
    }
    String eventId = envelope.eventId();
    if (repository.existsByEventId(eventId)) {
      log.debug("Duplicate ledger.balance.updated event {} — skipping", eventId);
      return;
    }

    LedgerBalanceUpdatedEventData data = envelope.data();
    EventMetadata metadata = envelope.metadata();
    String accountId = data.accountId() == null ? null : data.accountId().toString();

    BalanceHistory entity = new BalanceHistory();
    entity.setId(Ids.generate());
    entity.setEventId(eventId);
    entity.setAccountId(accountId);
    entity.setAvailableBalance(data.availableBalance());
    entity.setPendingBalance(data.pendingBalance());
    entity.setReservedBalance(data.reservedBalance());
    entity.setTotalBalance(data.totalBalance());
    entity.setTotalDebits(data.totalDebits());
    entity.setTotalCredits(data.totalCredits());
    entity.setLastEntrySequence(data.lastEntrySequence());
    entity.setBalanceAsOf(data.balanceAsOf());
    entity.setCurrency(lookupCurrency(accountId));
    entity.setIdempotencyKey(metadata == null ? null : metadata.idempotencyKey());
    entity.setLedgerCorrelationId(metadata == null ? null : metadata.correlationId());
    entity.setTenantId(metadata == null ? null : metadata.tenantId());
    entity.setOccurredAt(envelope.timestamp());
    entity.setReceivedAt(Instant.now());
    entity.setPayloadJson(serialize(envelope));

    try {
      repository.save(entity);
      log.debug(
          "Projected balance update account={} seq={}", accountId, data.lastEntrySequence());
    } catch (DataIntegrityViolationException e) {
      log.debug("Concurrent duplicate balance event {} — already projected", eventId);
    }
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

  private String serialize(EventEnvelope<LedgerBalanceUpdatedEventData> envelope) {
    try {
      return kafkaObjectMapper.writeValueAsString(envelope);
    } catch (JsonProcessingException e) {
      log.warn("Failed to serialize balance-history payload for event {}", envelope.eventId());
      return null;
    }
  }
}
