package com.softspark.chaos.ledgerproxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.softspark.chaos.ledgerproxy.dto.ReservationResponse;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit tests for {@link ReservationLookup} (poll until present or timeout). */
@ExtendWith(MockitoExtension.class)
@DisplayName("ReservationLookup")
class ReservationLookupTest {

  @Mock private LedgerClient ledgerClient;

  private ReservationResponse reservation(String id, String ref) {
    return new ReservationResponse(
        id,
        "VA-ORG",
        ref,
        "SINGLE",
        "ACTIVE",
        new BigDecimal("100"),
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        null,
        null,
        null,
        null);
  }

  @Test
  void should_returnReservationId_when_present() {
    var lookup = new ReservationLookup(ledgerClient, 5L, 200L);
    when(ledgerClient.getReservations(anyString(), anyString(), anyString()))
        .thenReturn(List.of(reservation("RES-1", "TX-1")));

    assertThat(lookup.find("", "VA-ORG", "TX-1")).contains("RES-1");
  }

  @Test
  void should_returnEmpty_when_neverAppearsBeforeTimeout() {
    var lookup = new ReservationLookup(ledgerClient, 5L, 30L);
    when(ledgerClient.getReservations(anyString(), anyString(), anyString())).thenReturn(List.of());

    assertThat(lookup.find("", "VA-ORG", "TX-1", Duration.ofMillis(30))).isEmpty();
  }

  @Test
  void should_swallowLedgerErrorsAndTimeout_when_ledgerUnreachable() {
    var lookup = new ReservationLookup(ledgerClient, 5L, 30L);
    when(ledgerClient.getReservations(anyString(), anyString(), anyString()))
        .thenThrow(new RuntimeException("ledger down"));

    assertThat(lookup.find("", "VA-ORG", "TX-1", Duration.ofMillis(30))).isEmpty();
  }
}
