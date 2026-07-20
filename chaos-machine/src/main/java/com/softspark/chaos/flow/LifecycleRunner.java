package com.softspark.chaos.flow;

import com.softspark.chaos.base.Ids;
import com.softspark.chaos.flow.builder.FlowCatalogProvider;
import com.softspark.chaos.flow.dto.AutogenRule;
import com.softspark.chaos.flow.dto.CarryOver;
import com.softspark.chaos.flow.dto.FieldKind;
import com.softspark.chaos.flow.dto.FlowCatalogEntry;
import com.softspark.chaos.flow.dto.FlowFieldDescriptor;
import com.softspark.chaos.flow.dto.FlowLifecycle;
import com.softspark.chaos.flow.model.FlowType;
import com.softspark.chaos.ledgerproxy.ReservationLookup;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/**
 * Orchestrates a single RANDOM-outcome lifecycle: publish {@code initiated}, resolve the
 * disbursement {@code reservation_id}, decide SUCCEED/FAIL, then publish {@code completed}/
 * {@code failed} carrying the initiated values forward.
 *
 * <p>Each invocation mints a distinct lifecycle by re-rolling the initiated phase's autogen ids
 * (so every run uses a distinct {@code transaction_id}/{@code settlement_request_id}). The secondary
 * phase is assembled by collapsing its catalog descriptors to their defaults/autogen values and then
 * applying the {@link FlowLifecycle#carryOver()} map (VA carry-overs route to slot overrides; the
 * rest to flow fields). For disbursement, {@code reservation_id} is resolved via
 * {@link ReservationLookup} between the two publishes, falling back to an autogen placeholder on
 * timeout (the ledger ignores it). The outcome is decided by {@link OutcomeDecider} (deterministic).
 */
@Component
public class LifecycleRunner {

  private static final Logger log = LoggerFactory.getLogger(LifecycleRunner.class);

  private static final String PUBLISHED = "PUBLISHED";

  private final FlowEngine flowEngine;
  private final ReservationLookup reservationLookup;
  private final OutcomeDecider outcomeDecider;
  private final FlowCatalogProvider catalogProvider;

  public LifecycleRunner(
      FlowEngine flowEngine,
      ReservationLookup reservationLookup,
      OutcomeDecider outcomeDecider,
      FlowCatalogProvider catalogProvider) {
    this.flowEngine = flowEngine;
    this.reservationLookup = reservationLookup;
    this.outcomeDecider = outcomeDecider;
    this.catalogProvider = catalogProvider;
  }

  /**
   * The outcome of running one lifecycle.
   *
   * @param success whether both the initiated and secondary publishes succeeded
   * @param initiatedEventId the published initiated event id (or last attempt)
   * @param secondaryEventId the published secondary event id, or {@code null} if not reached
   * @param succeeded whether the random outcome was SUCCEED ({@code completed}) vs FAIL
   * @param placeholderReservation whether a placeholder {@code reservation_id} was used (timeout)
   */
  public record Result(
      boolean success,
      String initiatedEventId,
      @Nullable String secondaryEventId,
      boolean succeeded,
      boolean placeholderReservation) {}

  /**
   * Runs one full lifecycle.
   *
   * @param baseInitiated the operator-supplied initiated intent (shared across the run)
   * @param lifecycle the lifecycle grouping (phases + carry-over)
   * @param seed the per-run seed for the outcome decision
   * @param index the zero-based lifecycle index within the run
   * @param total the total lifecycle count (for the history label)
   * @return the run result
   */
  public Result runOne(
      FlowRequest baseInitiated, FlowLifecycle lifecycle, long seed, int index, int total) {
    List<FlowFieldDescriptor> initiatedDescriptors = descriptorsFor(lifecycle.initiated());

    // 1. Mint a distinct lifecycle: re-roll the initiated autogen ids (fresh transaction_id, etc.).
    Map<String, Object> initFields = new LinkedHashMap<>(baseInitiated.flowFields());
    for (FlowFieldDescriptor d : initiatedDescriptors) {
      if (d.autogen() == AutogenRule.UUID_V4) {
        initFields.put(d.name(), Ids.generateUUID());
      } else if (d.autogen() == AutogenRule.ULID) {
        initFields.put(d.name(), Ids.generateULID());
      }
    }

    FlowRequest initiated =
        FlowRequestBuilder.builder()
            .flowType(lifecycle.initiated())
            .correlationId(baseInitiated.correlationId())
            .tenantId(baseInitiated.tenantId())
            .channel(baseInitiated.channel())
            .amount(baseInitiated.amount())
            .grossAmount(baseInitiated.grossAmount())
            .netAmount(baseInitiated.netAmount())
            .currency(baseInitiated.currency())
            .slotOverrides(baseInitiated.slotOverrides())
            .chaos(null)
            .flowFields(initFields)
            .fees(baseInitiated.fees())
            .build();

    String label = "LIFECYCLE:" + lifecycle.label() + ":" + (index + 1) + "/" + total;
    FlowResult initResult = flowEngine.execute(initiated, label + ":initiated");
    if (!PUBLISHED.equals(initResult.status())) {
      log.warn(
          "Lifecycle {}/{} initiated publish failed: {}", index + 1, total, initResult.error());
      return new Result(false, initResult.eventId(), null, false, false);
    }

    // 2. Decide the outcome and resolve the secondary phase.
    boolean succeeds = outcomeDecider.succeeds(seed, index);
    FlowType secondaryType = succeeds ? lifecycle.completed() : lifecycle.failed();
    List<FlowFieldDescriptor> secondaryDescriptors = descriptorsFor(secondaryType);

    // 3. Disbursement: resolve reservation_id (real via poll, else autogen placeholder).
    String reservationId = null;
    boolean placeholder = false;
    if (isDisbursement(lifecycle)) {
      String orgVa = asString(initFields.get("virtual_account_id"));
      String transactionRef = asString(initFields.get("transaction_id"));
      Optional<String> resolved = reservationLookup.find("", orgVa, transactionRef);
      if (resolved.isPresent()) {
        reservationId = resolved.get();
      } else {
        reservationId = Ids.generateUUID();
        placeholder = true;
        log.info(
            "Lifecycle {}/{}: reservation not found for tx {}, using placeholder",
            index + 1,
            total,
            transactionRef);
      }
    }

    // 4. Collapse the secondary descriptors to defaults/autogen, then apply carry-over.
    Map<String, Object> secondaryFields = new LinkedHashMap<>();
    Map<String, String> secondarySlots = new LinkedHashMap<>();
    for (FlowFieldDescriptor d : secondaryDescriptors) {
      if (d.kind() == FieldKind.VA_REF || d.kind() == FieldKind.FEE_LIST) {
        continue;
      }
      if (d.autogen() == AutogenRule.UUID_V4) {
        secondaryFields.put(d.name(), Ids.generateUUID());
      } else if (d.autogen() == AutogenRule.ULID) {
        secondaryFields.put(d.name(), Ids.generateULID());
      } else if (d.defaultValue() != null) {
        secondaryFields.put(d.name(), d.defaultValue());
      }
    }
    for (CarryOver carry : lifecycle.carryOver()) {
      Object value = resolveFrom(baseInitiated, initFields, carry.fromField());
      if (value == null || value.toString().isBlank()) {
        continue;
      }
      FlowFieldDescriptor target = findDescriptor(secondaryDescriptors, carry.toField());
      if (target == null) {
        continue; // the secondary form does not declare this field — skip (per CarryOver contract)
      }
      if (target.kind() == FieldKind.VA_REF && target.slotName() != null) {
        secondarySlots.put(target.slotName(), value.toString());
      } else {
        secondaryFields.put(carry.toField(), value.toString());
      }
    }
    if (reservationId != null) {
      secondaryFields.put("reservation_id", reservationId);
    }

    FlowRequest secondary =
        FlowRequestBuilder.builder()
            .flowType(secondaryType)
            .correlationId(baseInitiated.correlationId())
            .tenantId(baseInitiated.tenantId())
            .channel(baseInitiated.channel())
            .amount(null)
            .grossAmount(null)
            .netAmount(null)
            .currency(null)
            .slotOverrides(secondarySlots)
            .chaos(null)
            .flowFields(secondaryFields)
            .fees(null)
            .build();

    String secondaryPhase = succeeds ? "completed" : "failed";
    FlowResult secondaryResult = flowEngine.execute(secondary, label + ":" + secondaryPhase);
    boolean success = PUBLISHED.equals(secondaryResult.status());
    if (!success) {
      log.warn(
          "Lifecycle {}/{} {} publish failed: {}",
          index + 1,
          total,
          secondaryPhase,
          secondaryResult.error());
    }
    return new Result(
        success, initResult.eventId(), secondaryResult.eventId(), succeeds, placeholder);
  }

  private boolean isDisbursement(FlowLifecycle lifecycle) {
    return lifecycle.initiated() == FlowType.DISBURSEMENT_INITIATED;
  }

  /** Resolves a carry-over source value from the initiated flow fields, then top-level fields. */
  @Nullable
  private Object resolveFrom(FlowRequest base, Map<String, Object> initFields, String field) {
    if (initFields.containsKey(field)) {
      return initFields.get(field);
    }
    return switch (field) {
      case "currency" -> base.currency();
      case "amount" -> base.amount();
      case "correlation_id" -> base.correlationId();
      default -> base.slotOverrides().get(field);
    };
  }

  @Nullable
  private FlowFieldDescriptor findDescriptor(List<FlowFieldDescriptor> descriptors, String name) {
    for (FlowFieldDescriptor d : descriptors) {
      if (d.name().equals(name)) {
        return d;
      }
    }
    return null;
  }

  private List<FlowFieldDescriptor> descriptorsFor(FlowType flowType) {
    return catalogProvider.catalog().stream()
        .filter(entry -> entry.flowType() == flowType)
        .findFirst()
        .map(FlowCatalogEntry::fields)
        .orElse(List.of());
  }

  private static String asString(@Nullable Object value) {
    return value != null ? value.toString() : "";
  }
}
