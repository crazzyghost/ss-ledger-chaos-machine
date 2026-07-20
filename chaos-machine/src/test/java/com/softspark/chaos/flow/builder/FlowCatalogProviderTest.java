package com.softspark.chaos.flow.builder;

import static org.assertj.core.api.Assertions.assertThat;

import com.softspark.chaos.flow.dto.AutogenRule;
import com.softspark.chaos.flow.dto.BatchDisbursementGroup;
import com.softspark.chaos.flow.dto.CarryOver;
import com.softspark.chaos.flow.dto.FieldKind;
import com.softspark.chaos.flow.dto.FlowCatalogEntry;
import com.softspark.chaos.flow.dto.FlowFieldDescriptor;
import com.softspark.chaos.flow.dto.FlowLifecycle;
import com.softspark.chaos.flow.dto.InferenceRule;
import com.softspark.chaos.flow.model.FlowType;
import com.softspark.chaos.kafka.TopicCatalog;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/** Unit tests for {@link FlowCatalogProvider} — Phase 014 descriptors, lifecycle, slot coverage. */
@DisplayName("FlowCatalogProvider")
class FlowCatalogProviderTest {

  private final FlowCatalogProvider provider = new FlowCatalogProvider(new TopicCatalog());

  private FlowCatalogEntry entry(FlowType type) {
    return provider.catalog().stream().filter(e -> e.flowType() == type).findFirst().orElseThrow();
  }

  @Test
  void should_exposeExactlyNineRunnerVisibleFlows_when_catalogBuilt() {
    Set<FlowType> visible =
        provider.catalog().stream()
            .filter(FlowCatalogEntry::runnerVisible)
            .map(FlowCatalogEntry::flowType)
            .collect(Collectors.toSet());

    assertThat(visible)
        .containsExactlyInAnyOrder(
            FlowType.TOPUP_CONFIRMED,
            FlowType.TRANSFER_REQUESTED,
            FlowType.TREASURY_PREFUND_COMPLETED,
            FlowType.TREASURY_SWEEP_COMPLETED,
            FlowType.TREASURY_TRANSFER_COMPLETED,
            FlowType.COLLECTION_COMPLETED,
            FlowType.SETTLEMENT_INITIATED,
            FlowType.DISBURSEMENT_INITIATED,
            FlowType.DISBURSEMENT_BATCH_RESERVATION_REQUEST);
  }

  @Test
  void should_hideLifecycleSecondaryPhases_when_catalogBuilt() {
    assertThat(entry(FlowType.SETTLEMENT_COMPLETED).runnerVisible()).isFalse();
    assertThat(entry(FlowType.SETTLEMENT_FAILED).runnerVisible()).isFalse();
    assertThat(entry(FlowType.DISBURSEMENT_COMPLETED).runnerVisible()).isFalse();
    assertThat(entry(FlowType.DISBURSEMENT_FAILED).runnerVisible()).isFalse();
  }

  @Test
  void should_attachLifecycleToInitiatedOnly_when_catalogBuilt() {
    assertThat(entry(FlowType.COLLECTION_COMPLETED).lifecycle()).isNull();
    assertThat(entry(FlowType.SETTLEMENT_COMPLETED).lifecycle()).isNull();

    FlowLifecycle settlement = entry(FlowType.SETTLEMENT_INITIATED).lifecycle();
    assertThat(settlement).isNotNull();
    assertThat(settlement.label()).isEqualTo("Settlement");
    assertThat(settlement.initiated()).isEqualTo(FlowType.SETTLEMENT_INITIATED);
    assertThat(settlement.completed()).isEqualTo(FlowType.SETTLEMENT_COMPLETED);
    assertThat(settlement.failed()).isEqualTo(FlowType.SETTLEMENT_FAILED);

    FlowLifecycle disbursement = entry(FlowType.DISBURSEMENT_INITIATED).lifecycle();
    assertThat(disbursement).isNotNull();
    assertThat(disbursement.label()).isEqualTo("Disbursement");
    assertThat(disbursement.completed()).isEqualTo(FlowType.DISBURSEMENT_COMPLETED);
    assertThat(disbursement.failed()).isEqualTo(FlowType.DISBURSEMENT_FAILED);
  }

  @Test
  void should_declareCarryOverForBothSecondaryPhases_when_disbursementLifecycle() {
    List<CarryOver> carry = entry(FlowType.DISBURSEMENT_INITIATED).lifecycle().carryOver();
    assertThat(carry).contains(new CarryOver("transaction_id", "transaction_id"));
    // virtual_account_id maps to source_va_id (completed) AND virtual_account_id (failed)
    assertThat(carry).contains(new CarryOver("virtual_account_id", "source_va_id"));
    assertThat(carry).contains(new CarryOver("virtual_account_id", "virtual_account_id"));
    assertThat(carry).contains(new CarryOver("principal_amount", "principal_amount"));
  }

  @Test
  void should_renderCollectionFeeList_when_collectionDescriptors() {
    FlowFieldDescriptor fees = field(FlowType.COLLECTION_COMPLETED, "fees");
    assertThat(fees.kind()).isEqualTo(FieldKind.FEE_LIST);
    assertThat(fees.required()).isTrue();

    FlowFieldDescriptor net = field(FlowType.COLLECTION_COMPLETED, "amount");
    assertThat(net.label()).isEqualTo("Net Amount");
    assertThat(net.defaultValue()).isEqualTo("1000.0000");

    FlowFieldDescriptor merchantRef = field(FlowType.COLLECTION_COMPLETED, "merchant_ref_id");
    assertThat(merchantRef.autogen()).isEqualTo(AutogenRule.ULID);
  }

  @Test
  void should_renderCountryAndDerivedCorridor_when_disbursementInitiatedDescriptors() {
    assertThat(field(FlowType.DISBURSEMENT_INITIATED, "source_country").kind())
        .isEqualTo(FieldKind.COUNTRY);
    assertThat(field(FlowType.DISBURSEMENT_INITIATED, "destination_country").defaultValue())
        .isEqualTo("GH");
    assertThat(field(FlowType.DISBURSEMENT_INITIATED, "corridor").inference())
        .isEqualTo(InferenceRule.CORRIDOR_FROM_COUNTRIES);
    // The org VA routes to flowFields (slotName == null), not a slot.
    assertThat(field(FlowType.DISBURSEMENT_INITIATED, "virtual_account_id").slotName()).isNull();
  }

  @Test
  void should_attachBatchGroupToReservationOnly_when_catalogBuilt() {
    // Reservation is the single runnerVisible batch entry and the only one carrying the group.
    FlowCatalogEntry reservation = entry(FlowType.DISBURSEMENT_BATCH_RESERVATION_REQUEST);
    assertThat(reservation.runnerVisible()).isTrue();
    assertThat(reservation.lifecycle()).isNull();
    BatchDisbursementGroup group = reservation.batchGroup();
    assertThat(group).isNotNull();
    assertThat(group.label()).isEqualTo("Batch Disbursement");
    assertThat(group.reservation()).isEqualTo(FlowType.DISBURSEMENT_BATCH_RESERVATION_REQUEST);
    assertThat(group.itemRequest()).isEqualTo(FlowType.DISBURSEMENT_BATCH_ITEM_REQUEST);
    assertThat(group.itemCompleted()).isEqualTo(FlowType.DISBURSEMENT_BATCH_ITEM_COMPLETED);
    assertThat(group.itemFailed()).isEqualTo(FlowType.DISBURSEMENT_BATCH_ITEM_FAILED);
    assertThat(group.reservationToItem())
        .contains(
            new CarryOver("batch_id", "batch_id"),
            new CarryOver("source_va_id", "virtual_account_id"),
            new CarryOver("reservation_id", "reservation_id"),
            new CarryOver("disbursement_subtype", "disbursement_subtype"));
    assertThat(group.itemRequestToTerminal())
        .contains(
            new CarryOver("item_id", "item_id"),
            new CarryOver("item_sequence", "item_sequence"),
            new CarryOver("principal_amount", "principal_amount"),
            new CarryOver("merchant_item_ref", "merchant_item_ref"));

    // The three other batch phases keep full descriptors but are hidden and groupless.
    for (FlowType t :
        List.of(
            FlowType.DISBURSEMENT_BATCH_ITEM_REQUEST,
            FlowType.DISBURSEMENT_BATCH_ITEM_COMPLETED,
            FlowType.DISBURSEMENT_BATCH_ITEM_FAILED)) {
      assertThat(entry(t).runnerVisible()).isFalse();
      assertThat(entry(t).batchGroup()).isNull();
      assertThat(entry(t).fields()).isNotEmpty();
    }
  }

  @Test
  void should_atMostOneGroupingPerEntry_when_catalogBuilt() {
    for (FlowCatalogEntry e : provider.catalog()) {
      boolean both = e.lifecycle() != null && e.batchGroup() != null;
      assertThat(both).as("entry %s has both lifecycle and batchGroup", e.flowType()).isFalse();
    }
  }

  @Test
  void should_useIntegerKindForItemCount_when_batchReservationDescriptors() {
    FlowFieldDescriptor itemCount =
        field(FlowType.DISBURSEMENT_BATCH_RESERVATION_REQUEST, "item_count");
    assertThat(itemCount.kind()).isEqualTo(FieldKind.INTEGER);
    assertThat(itemCount.required()).isTrue();
    assertThat(itemCount.defaultValue()).isEqualTo("4");

    FlowFieldDescriptor totalPrincipal =
        field(FlowType.DISBURSEMENT_BATCH_RESERVATION_REQUEST, "total_principal_amount");
    assertThat(totalPrincipal.kind()).isEqualTo(FieldKind.AMOUNT);
    assertThat(totalPrincipal.defaultValue()).isEqualTo("1000.0000");

    FlowFieldDescriptor failureCode =
        field(FlowType.DISBURSEMENT_BATCH_ITEM_FAILED, "failure_code");
    assertThat(failureCode.kind()).isEqualTo(FieldKind.SELECT);
    assertThat(failureCode.options()).hasSize(7).contains("RESERVATION_MISSING");
  }

  @Test
  void should_haveBootstrapSlotForEveryRunnerVaRefSlot_when_catalogBuilt() throws Exception {
    Set<String> seeded = seededSlots();
    for (FlowCatalogEntry e : provider.catalog()) {
      // Only flows reachable by the runner/wizard need seeded slots.
      boolean reachable =
          e.runnerVisible() || isLifecycleSecondary(e.flowType()) || isBatchPhase(e.flowType());
      if (!reachable) {
        continue;
      }
      for (FlowFieldDescriptor d : e.fields()) {
        if (d.kind() == FieldKind.VA_REF && d.slotName() != null) {
          assertThat(seeded)
              .as("bootstrap slot for %s.%s", e.flowType(), d.slotName())
              .contains(e.flowType().name() + "." + d.slotName());
        }
      }
    }
  }

  private boolean isLifecycleSecondary(FlowType type) {
    return type == FlowType.SETTLEMENT_COMPLETED
        || type == FlowType.SETTLEMENT_FAILED
        || type == FlowType.DISBURSEMENT_COMPLETED
        || type == FlowType.DISBURSEMENT_FAILED;
  }

  private boolean isBatchPhase(FlowType type) {
    return type == FlowType.DISBURSEMENT_BATCH_RESERVATION_REQUEST
        || type == FlowType.DISBURSEMENT_BATCH_ITEM_REQUEST
        || type == FlowType.DISBURSEMENT_BATCH_ITEM_COMPLETED
        || type == FlowType.DISBURSEMENT_BATCH_ITEM_FAILED;
  }

  private FlowFieldDescriptor field(FlowType type, String name) {
    return entry(type).fields().stream()
        .filter(f -> f.name().equals(name))
        .findFirst()
        .orElseThrow(() -> new AssertionError("missing descriptor " + name + " on " + type));
  }

  @SuppressWarnings("unchecked")
  private Set<String> seededSlots() throws Exception {
    try (InputStream in = getClass().getResourceAsStream("/chaos-bootstrap.yml")) {
      Map<String, Object> root = new Yaml().load(in);
      Map<String, Object> chaos = (Map<String, Object>) root.get("chaos");
      Map<String, Object> bootstrap = (Map<String, Object>) chaos.get("bootstrap");
      List<Map<String, Object>> slots = (List<Map<String, Object>>) bootstrap.get("flow-slots");
      return slots.stream()
          .map(s -> s.get("flowType") + "." + s.get("slotName"))
          .collect(Collectors.toSet());
    }
  }
}
