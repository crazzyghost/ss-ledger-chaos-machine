package com.softspark.chaos.flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.softspark.chaos.flow.builder.FlowCatalogProvider;
import com.softspark.chaos.flow.dto.FlowCatalogEntry;
import com.softspark.chaos.flow.dto.FlowLifecycle;
import com.softspark.chaos.flow.model.FlowType;
import com.softspark.chaos.kafka.TopicCatalog;
import com.softspark.chaos.ledgerproxy.ReservationLookup;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit tests for {@link LifecycleRunner} (two-publish orchestration, carry-over, reservation). */
@ExtendWith(MockitoExtension.class)
@DisplayName("LifecycleRunner")
class LifecycleRunnerTest {

  @Mock private FlowEngine flowEngine;
  @Mock private ReservationLookup reservationLookup;
  @Mock private OutcomeDecider outcomeDecider;

  private LifecycleRunner runner;
  private FlowLifecycle disbursement;

  @BeforeEach
  void setUp() {
    var catalogProvider = new FlowCatalogProvider(new TopicCatalog());
    runner = new LifecycleRunner(flowEngine, reservationLookup, outcomeDecider, catalogProvider);
    disbursement =
        catalogProvider.catalog().stream()
            .filter(e -> e.flowType() == FlowType.DISBURSEMENT_INITIATED)
            .map(FlowCatalogEntry::lifecycle)
            .findFirst()
            .orElseThrow();
    when(flowEngine.execute(any(), anyString()))
        .thenReturn(new FlowResult("evt", "topic", 0, 0L, "PUBLISHED", "hist", null, null));
  }

  private FlowRequest baseInitiated() {
    return FlowRequestBuilder.builder()
        .flowType(FlowType.DISBURSEMENT_INITIATED)
        .currency("GHS")
        .slotOverrides(Map.of())
        .flowFields(
            new java.util.LinkedHashMap<>(
                Map.of(
                    "transaction_id", "TX-SEED",
                    "virtual_account_id", "VA-ORG",
                    "merchant_id", "ORG-1",
                    "merchant_ref_id", "MR-1",
                    "principal_amount", "5000",
                    "credit_provider_id", "PROVIDER_GH",
                    "credit_account_id", "0240000000")))
        .build();
  }

  @Test
  void should_publishInitiatedThenCompleted_when_outcomeSucceeds() {
    when(outcomeDecider.succeeds(anyLong(), anyInt())).thenReturn(true);
    when(reservationLookup.find(anyString(), eq("VA-ORG"), anyString()))
        .thenReturn(Optional.of("RES-REAL"));

    var result = runner.runOne(baseInitiated(), disbursement, 42L, 0, 1);

    var captor = ArgumentCaptor.forClass(FlowRequest.class);
    verify(flowEngine, times(2)).execute(captor.capture(), anyString());
    FlowRequest initiated = captor.getAllValues().get(0);
    FlowRequest secondary = captor.getAllValues().get(1);

    assertThat(initiated.flowType()).isEqualTo(FlowType.DISBURSEMENT_INITIATED);
    assertThat(secondary.flowType()).isEqualTo(FlowType.DISBURSEMENT_COMPLETED);

    // A fresh transaction id was minted and carried forward unchanged.
    String txId = initiated.flowFields().get("transaction_id").toString();
    assertThat(txId).isNotEqualTo("TX-SEED");
    assertThat(secondary.flowFields()).containsEntry("transaction_id", txId);
    // The org VA carried into the completed source slot, and the real reservation prefilled.
    assertThat(secondary.slotOverrides()).containsEntry("source", "VA-ORG");
    assertThat(secondary.flowFields()).containsEntry("reservation_id", "RES-REAL");

    assertThat(result.success()).isTrue();
    assertThat(result.succeeded()).isTrue();
    assertThat(result.placeholderReservation()).isFalse();
  }

  @Test
  void should_publishInitiatedThenFailed_when_outcomeFails() {
    when(outcomeDecider.succeeds(anyLong(), anyInt())).thenReturn(false);
    when(reservationLookup.find(anyString(), eq("VA-ORG"), anyString()))
        .thenReturn(Optional.of("RES-REAL"));

    var result = runner.runOne(baseInitiated(), disbursement, 42L, 1, 1);

    var captor = ArgumentCaptor.forClass(FlowRequest.class);
    verify(flowEngine, times(2)).execute(captor.capture(), anyString());
    FlowRequest secondary = captor.getAllValues().get(1);

    assertThat(secondary.flowType()).isEqualTo(FlowType.DISBURSEMENT_FAILED);
    // For failed, the VA carries into virtual_account_id (flow field), not a slot.
    assertThat(secondary.flowFields()).containsEntry("virtual_account_id", "VA-ORG");
    assertThat(result.succeeded()).isFalse();
  }

  @Test
  void should_usePlaceholderReservation_when_lookupTimesOut() {
    when(outcomeDecider.succeeds(anyLong(), anyInt())).thenReturn(true);
    when(reservationLookup.find(anyString(), eq("VA-ORG"), anyString()))
        .thenReturn(Optional.empty());

    var result = runner.runOne(baseInitiated(), disbursement, 42L, 0, 1);

    var captor = ArgumentCaptor.forClass(FlowRequest.class);
    verify(flowEngine, times(2)).execute(captor.capture(), anyString());
    FlowRequest secondary = captor.getAllValues().get(1);

    assertThat(secondary.flowFields().get("reservation_id")).asString().isNotBlank();
    assertThat(result.placeholderReservation()).isTrue();
    assertThat(result.success()).isTrue();
  }
}
