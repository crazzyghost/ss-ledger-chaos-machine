package com.softspark.chaos.flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.softspark.chaos.flow.model.FlowType;
import com.softspark.chaos.ledgerproxy.BatchReservationLookup;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit tests for {@link BatchDisbursementRunner} — reservation resolution + per-item publishing. */
@ExtendWith(MockitoExtension.class)
@DisplayName("BatchDisbursementRunner")
class BatchDisbursementRunnerTest {

  @Mock private FlowEngine flowEngine;
  @Mock private BatchReservationLookup reservationLookup;

  private FlowResult published() {
    return new FlowResult("EVT", "topic", 0, 0L, "PUBLISHED", "HID", null, null);
  }

  private BatchDisbursementRunner.Plan plan(List<BatchDisbursementRunner.Item> items) {
    return new BatchDisbursementRunner.Plan(
        "",
        "org_123",
        "CORR-1",
        "BATCH-1",
        "BCORR-1",
        "ORG-1",
        "VA-ORG",
        "VA-FLOAT",
        "GHS",
        new BigDecimal("1000.0000"),
        new BigDecimal("10"),
        items.size(),
        "DOMESTIC",
        "BATCH-REF-1",
        null,
        "chaos-operator",
        "ab:cd:ef:00",
        "PROVIDER_GH",
        "0240000000",
        "PROVIDER_GH",
        null,
        null,
        "VA-FEE",
        "Recipient invalid",
        "RECIPIENT_INVALID",
        null,
        items);
  }

  @Test
  void should_returnResolvedReservationId_when_present() {
    var runner = new BatchDisbursementRunner(flowEngine, reservationLookup);
    when(flowEngine.execute(any(FlowRequest.class), anyString())).thenReturn(published());
    when(reservationLookup.find(anyString(), anyString())).thenReturn(Optional.of("RES-BATCH-1"));

    String reservationId = runner.publishReservation(plan(List.of()));

    assertThat(reservationId).isEqualTo("RES-BATCH-1");
    ArgumentCaptor<FlowRequest> captor = ArgumentCaptor.forClass(FlowRequest.class);
    verify(flowEngine).execute(captor.capture(), anyString());
    assertThat(captor.getValue().flowType())
        .isEqualTo(FlowType.DISBURSEMENT_BATCH_RESERVATION_REQUEST);
    assertThat(captor.getValue().slotOverrides())
        .containsEntry("source", "VA-ORG")
        .containsEntry("destination", "VA-FLOAT");
  }

  @Test
  void should_fallBackToPlaceholder_when_reservationTimesOut() {
    var runner = new BatchDisbursementRunner(flowEngine, reservationLookup);
    when(flowEngine.execute(any(FlowRequest.class), anyString())).thenReturn(published());
    when(reservationLookup.find(anyString(), anyString())).thenReturn(Optional.empty());

    String reservationId = runner.publishReservation(plan(List.of()));

    assertThat(reservationId).isNotBlank();
  }

  @Test
  void should_publishRequestThenCompleted_when_itemPasses() {
    var runner = new BatchDisbursementRunner(flowEngine, reservationLookup);
    when(flowEngine.execute(any(FlowRequest.class), anyString())).thenReturn(published());
    var items =
        List.of(
            new BatchDisbursementRunner.Item(
                new BigDecimal("500.0000"), new BigDecimal("5"), true));

    BatchDisbursementRunner.ItemResult result = runner.runItem(plan(items), 0, "RES-1");

    assertThat(result.success()).isTrue();
    ArgumentCaptor<FlowRequest> captor = ArgumentCaptor.forClass(FlowRequest.class);
    verify(flowEngine, times(2)).execute(captor.capture(), anyString());
    List<FlowRequest> requests = captor.getAllValues();
    assertThat(requests.get(0).flowType()).isEqualTo(FlowType.DISBURSEMENT_BATCH_ITEM_REQUEST);
    assertThat(requests.get(1).flowType()).isEqualTo(FlowType.DISBURSEMENT_BATCH_ITEM_COMPLETED);
    assertThat(requests.get(0).flowFields()).containsEntry("batch_id", "BATCH-1");
    assertThat(requests.get(0).flowFields()).containsEntry("item_sequence", 1);
    assertThat(requests.get(1).flowFields()).containsEntry("reservation_id", "RES-1");
    assertThat(requests.get(1).fees()).hasSize(1);
  }

  @Test
  void should_publishRequestThenFailed_when_itemFails() {
    var runner = new BatchDisbursementRunner(flowEngine, reservationLookup);
    when(flowEngine.execute(any(FlowRequest.class), anyString())).thenReturn(published());
    var items =
        List.of(
            new BatchDisbursementRunner.Item(
                new BigDecimal("500.0000"), new BigDecimal("5"), false));

    BatchDisbursementRunner.ItemResult result = runner.runItem(plan(items), 0, "RES-1");

    assertThat(result.success()).isTrue();
    assertThat(result.passed()).isFalse();
    ArgumentCaptor<FlowRequest> captor = ArgumentCaptor.forClass(FlowRequest.class);
    verify(flowEngine, times(2)).execute(captor.capture(), anyString());
    List<FlowRequest> requests = captor.getAllValues();
    assertThat(requests.get(1).flowType()).isEqualTo(FlowType.DISBURSEMENT_BATCH_ITEM_FAILED);
    assertThat(requests.get(1).flowFields()).containsEntry("failure_code", "RECIPIENT_INVALID");
  }

  @Test
  void should_mintDistinctItemIds_when_runningMultipleItems() {
    var runner = new BatchDisbursementRunner(flowEngine, reservationLookup);
    when(flowEngine.execute(any(FlowRequest.class), anyString())).thenReturn(published());
    var items =
        List.of(
            new BatchDisbursementRunner.Item(new BigDecimal("500"), new BigDecimal("5"), true),
            new BatchDisbursementRunner.Item(new BigDecimal("500"), new BigDecimal("5"), true));
    var p = plan(items);

    runner.runItem(p, 0, "RES-1");
    runner.runItem(p, 1, "RES-1");

    ArgumentCaptor<FlowRequest> captor = ArgumentCaptor.forClass(FlowRequest.class);
    verify(flowEngine, times(4)).execute(captor.capture(), anyString());
    Object item0 = captor.getAllValues().get(0).flowFields().get("item_id");
    Object item1 = captor.getAllValues().get(2).flowFields().get("item_id");
    assertThat(item0).isNotEqualTo(item1);
    assertThat(captor.getAllValues().get(2).flowFields()).containsEntry("item_sequence", 2);
  }
}
