package com.softspark.chaos.balance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.softspark.chaos.account.model.VirtualAccount;
import com.softspark.chaos.balance.consumer.LedgerBalanceUpdatedEventData;
import com.softspark.chaos.balance.model.BalanceHistory;
import com.softspark.chaos.balance.repository.BalanceHistoryRepository;
import com.softspark.chaos.account.repository.VirtualAccountRepository;
import com.softspark.chaos.kafka.EventEnvelope;
import com.softspark.chaos.kafka.EventMetadata;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit tests for {@link BalanceHistoryProjectionService}. */
@ExtendWith(MockitoExtension.class)
@DisplayName("BalanceHistoryProjectionService")
class BalanceHistoryProjectionServiceTest {

  private static final UUID ACCOUNT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

  @Mock private BalanceHistoryRepository repository;
  @Mock private VirtualAccountRepository virtualAccountRepository;

  private BalanceHistoryProjectionService service;

  @BeforeEach
  void setUp() {
    ObjectMapper mapper = new ObjectMapper();
    mapper.registerModule(new JavaTimeModule());
    mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    service = new BalanceHistoryProjectionService(repository, virtualAccountRepository, mapper);
  }

  private EventEnvelope<LedgerBalanceUpdatedEventData> envelope(String eventId) {
    var data =
        new LedgerBalanceUpdatedEventData(
            ACCOUNT_ID,
            new BigDecimal("100.50"),
            new BigDecimal("10.00"),
            new BigDecimal("5.25"),
            new BigDecimal("115.75"),
            new BigDecimal("200.00"),
            new BigDecimal("315.75"),
            42L,
            LocalDateTime.parse("2026-06-27T10:00:00"));
    var meta = new EventMetadata("rand-corr", "journal-1:acct", "tenant-1");
    return new EventEnvelope<>(
        eventId,
        "ledger.balance.updated",
        Instant.parse("2026-06-27T10:00:00Z"),
        "ledger-service",
        "1.0",
        data,
        meta);
  }

  @Test
  @DisplayName("maps all buckets/sequence/as-of and backfills currency from the VA registry")
  void mapsAllFieldsWithCurrency() {
    when(repository.existsByEventId("evt-1")).thenReturn(false);
    var va = new VirtualAccount();
    va.setCurrency("GHS");
    when(virtualAccountRepository.findById(ACCOUNT_ID.toString())).thenReturn(Optional.of(va));

    service.project(envelope("evt-1"));

    ArgumentCaptor<BalanceHistory> captor = ArgumentCaptor.forClass(BalanceHistory.class);
    verify(repository).save(captor.capture());
    BalanceHistory saved = captor.getValue();
    assertThat(saved.getEventId()).isEqualTo("evt-1");
    assertThat(saved.getAccountId()).isEqualTo(ACCOUNT_ID.toString());
    assertThat(saved.getAvailableBalance()).isEqualByComparingTo("100.50");
    assertThat(saved.getPendingBalance()).isEqualByComparingTo("10.00");
    assertThat(saved.getReservedBalance()).isEqualByComparingTo("5.25");
    assertThat(saved.getTotalBalance()).isEqualByComparingTo("115.75");
    assertThat(saved.getTotalDebits()).isEqualByComparingTo("200.00");
    assertThat(saved.getTotalCredits()).isEqualByComparingTo("315.75");
    assertThat(saved.getLastEntrySequence()).isEqualTo(42L);
    assertThat(saved.getBalanceAsOf()).isEqualTo(LocalDateTime.parse("2026-06-27T10:00:00"));
    assertThat(saved.getCurrency()).isEqualTo("GHS");
    assertThat(saved.getIdempotencyKey()).isEqualTo("journal-1:acct");
    assertThat(saved.getOccurredAt()).isEqualTo(Instant.parse("2026-06-27T10:00:00Z"));
    assertThat(saved.getReceivedAt()).isNotNull();
  }

  @Test
  @DisplayName("currency is null when the VA is not yet projected")
  void nullCurrencyWhenVaMissing() {
    when(repository.existsByEventId("evt-2")).thenReturn(false);
    when(virtualAccountRepository.findById(ACCOUNT_ID.toString())).thenReturn(Optional.empty());

    service.project(envelope("evt-2"));

    ArgumentCaptor<BalanceHistory> captor = ArgumentCaptor.forClass(BalanceHistory.class);
    verify(repository).save(captor.capture());
    assertThat(captor.getValue().getCurrency()).isNull();
  }

  @Test
  @DisplayName("redelivery is a no-op")
  void idempotent() {
    when(repository.existsByEventId("evt-1")).thenReturn(true);

    service.project(envelope("evt-1"));

    verify(repository, never()).save(any());
  }

  @Test
  @DisplayName("null data is skipped")
  void nullDataSkipped() {
    var empty =
        new EventEnvelope<LedgerBalanceUpdatedEventData>(
            "evt-x", "ledger.balance.updated", Instant.now(), "ledger-service", "1.0", null, null);

    service.project(empty);

    verify(repository, never()).save(any());
  }
}
