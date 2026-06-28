package com.softspark.chaos.ledgerproxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.softspark.chaos.ledgerproxy.dto.DisbursementBatchSummaryDto;
import java.math.BigDecimal;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit tests for {@link BatchReservationLookup} (poll the batch summary until present or timeout). */
@ExtendWith(MockitoExtension.class)
@DisplayName("BatchReservationLookup")
class BatchReservationLookupTest {

  @Mock private LedgerClient ledgerClient;

  private DisbursementBatchSummaryDto summary(String reservationId, String status) {
    return new DisbursementBatchSummaryDto(
        "BATCH-1",
        reservationId,
        status,
        "GHS",
        4,
        0,
        0,
        4,
        new BigDecimal("1000.0000"),
        new BigDecimal("10"),
        new BigDecimal("1010.0000"),
        null,
        null,
        null);
  }

  @Test
  void should_returnReservationId_when_present() {
    var lookup = new BatchReservationLookup(ledgerClient, 5L, 200L);
    when(ledgerClient.getDisbursementBatch(anyString(), anyString()))
        .thenReturn(summary("RES-BATCH-1", "INITIATED"));

    assertThat(lookup.find("", "BATCH-1")).contains("RES-BATCH-1");
  }

  @Test
  void should_returnEmpty_when_reservationNeverLandsBeforeTimeout() {
    var lookup = new BatchReservationLookup(ledgerClient, 5L, 30L);
    when(ledgerClient.getDisbursementBatch(anyString(), anyString()))
        .thenReturn(summary(null, "FAILED"));

    assertThat(lookup.find("", "BATCH-1", Duration.ofMillis(30))).isEmpty();
  }

  @Test
  void should_swallowLedgerErrorsAndTimeout_when_ledgerUnreachable() {
    var lookup = new BatchReservationLookup(ledgerClient, 5L, 30L);
    when(ledgerClient.getDisbursementBatch(anyString(), anyString()))
        .thenThrow(new RuntimeException("ledger down"));

    assertThat(lookup.find("", "BATCH-1", Duration.ofMillis(30))).isEmpty();
  }

  @Test
  void should_returnLatestSummary_when_summaryRead() {
    var lookup = new BatchReservationLookup(ledgerClient, 5L, 200L);
    when(ledgerClient.getDisbursementBatch(anyString(), anyString()))
        .thenReturn(summary("RES-BATCH-1", "IN_PROGRESS"));

    assertThat(lookup.summary("", "BATCH-1"))
        .get()
        .extracting(DisbursementBatchSummaryDto::status)
        .isEqualTo("IN_PROGRESS");
  }

  @Test
  void should_returnEmptySummary_when_ledgerReadFails() {
    var lookup = new BatchReservationLookup(ledgerClient, 5L, 200L);
    when(ledgerClient.getDisbursementBatch(anyString(), anyString()))
        .thenThrow(new RuntimeException("ledger down"));

    assertThat(lookup.summary("", "BATCH-1")).isEmpty();
  }
}
