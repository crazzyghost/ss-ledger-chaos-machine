package com.softspark.chaos.flow.controller;

import com.softspark.chaos.batch.dto.BatchRunResponse;
import com.softspark.chaos.exception.BadRequestException;
import com.softspark.chaos.flow.BatchDisbursementRunService;
import com.softspark.chaos.flow.FlowEngine;
import com.softspark.chaos.flow.FlowRequest;
import com.softspark.chaos.flow.FlowRequestBuilder;
import com.softspark.chaos.flow.FlowResult;
import com.softspark.chaos.flow.LifecycleRunService;
import com.softspark.chaos.flow.NTimesRunService;
import com.softspark.chaos.flow.NTimesSyncResult;
import com.softspark.chaos.flow.NTimesSyncRunner;
import com.softspark.chaos.flow.builder.FlowCatalogProvider;
import com.softspark.chaos.flow.chaos.ExecutionMode;
import com.softspark.chaos.flow.chaos.NTimesOptions;
import com.softspark.chaos.flow.chaos.Pacing;
import com.softspark.chaos.flow.dto.BatchDisbursementRunRequest;
import com.softspark.chaos.flow.dto.FlowCatalogEntry;
import com.softspark.chaos.flow.dto.FlowLifecycle;
import com.softspark.chaos.flow.dto.PublishFlowRequest;
import com.softspark.chaos.flow.model.FlowType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for the transaction flow engine.
 *
 * <p>Provides a single publish endpoint, a dedicated N-Times endpoint, and a catalog endpoint for
 * discovery.
 */
@RestController
@RequestMapping("/api/v0/flows")
@Tag(name = "Flow Engine", description = "Transaction flow publishing")
public class FlowController {

  private static final String N_TIMES_ROUTE_HINT =
      "chaos.nTimes is not supported on this endpoint; use POST /api/v0/flows/{flowType}/n-times";

  private final FlowEngine flowEngine;
  private final FlowCatalogProvider catalogProvider;
  private final NTimesSyncRunner nTimesSyncRunner;
  private final NTimesRunService nTimesRunService;
  private final LifecycleRunService lifecycleRunService;
  private final BatchDisbursementRunService batchDisbursementRunService;

  public FlowController(
      FlowEngine flowEngine,
      FlowCatalogProvider catalogProvider,
      NTimesSyncRunner nTimesSyncRunner,
      NTimesRunService nTimesRunService,
      LifecycleRunService lifecycleRunService,
      BatchDisbursementRunService batchDisbursementRunService) {
    this.flowEngine = flowEngine;
    this.catalogProvider = catalogProvider;
    this.nTimesSyncRunner = nTimesSyncRunner;
    this.nTimesRunService = nTimesRunService;
    this.lifecycleRunService = lifecycleRunService;
    this.batchDisbursementRunService = batchDisbursementRunService;
  }

  /**
   * Publishes a single transaction flow event.
   *
   * @param flowType the flow type to execute
   * @param request the publish request body
   * @return {@code 200} with the {@link FlowResult} on success; {@code 500} when publishing failed
   */
  @PostMapping("/{flowType}")
  @Operation(
      summary = "Publish a transaction flow event",
      description =
          "Resolves slots, builds an event envelope, optionally applies chaos, and "
              + "publishes to Kafka")
  public ResponseEntity<FlowResult> publish(
      @PathVariable FlowType flowType, @RequestBody PublishFlowRequest request) {

    if (request.chaos() != null && request.chaos().nTimes() != null) {
      throw new BadRequestException(N_TIMES_ROUTE_HINT);
    }

    FlowResult result = flowEngine.execute(toFlowRequest(flowType, request));
    return "PUBLISHED".equals(result.status())
        ? ResponseEntity.ok(result)
        : ResponseEntity.internalServerError().body(result);
  }

  /**
   * Runs a flow {@code count} times producing genuinely-distinct transactions against the same
   * accounts (N-Times strategy). The response depends on the execution mode: a {@code 200}
   * aggregate summary for {@code SYNC}, or a {@code 202} run handle for {@code ASYNC}.
   *
   * @param flowType the flow type to execute
   * @param request the publish request body; must carry {@code chaos.nTimes}
   * @return {@code 200} with an {@link NTimesSyncResult} (SYNC) or {@code 202} with a
   *     {@link BatchRunResponse} run handle (ASYNC)
   */
  @PostMapping("/{flowType}/n-times")
  @Operation(
      summary = "Run a flow N times (distinct transactions)",
      description =
          "Runs a flow count times against the same accounts, producing distinct transactions. "
              + "SYNC returns an aggregate summary; ASYNC returns a run handle to poll.")
  public ResponseEntity<?> nTimes(
      @PathVariable FlowType flowType, @RequestBody PublishFlowRequest request) {

    if (request.chaos() == null || request.chaos().nTimes() == null) {
      throw new BadRequestException(
          "The /n-times endpoint requires a chaos.nTimes configuration in the request body");
    }

    FlowRequest flowRequest = toFlowRequest(flowType, request);
    ExecutionMode mode = request.chaos().nTimes().mode();

    if (mode == ExecutionMode.ASYNC) {
      BatchRunResponse run = nTimesRunService.startRun(flowRequest);
      return ResponseEntity.accepted().body(run);
    }

    NTimesSyncResult result = nTimesSyncRunner.run(flowRequest);
    return ResponseEntity.ok(result);
  }

  /**
   * Runs {@code count} distinct RANDOM-outcome lifecycles for a Settlement/Disbursement transaction
   * type, unattended on the backend. Each lifecycle publishes {@code initiated} then — by a
   * deterministic random decision — {@code completed} or {@code failed}, carrying the initiated values
   * forward (and resolving the disbursement {@code reservation_id}). Always asynchronous: returns a
   * {@code 202} run handle to poll in the run-results view.
   *
   * @param flowType the lifecycle's {@code initiated} flow type ({@code SETTLEMENT_INITIATED} or
   *     {@code DISBURSEMENT_INITIATED})
   * @param request the initiated intent; {@code count}/pacing are read from {@code chaos.nTimes}
   * @return {@code 202} with a {@link BatchRunResponse} run handle
   * @throws BadRequestException if {@code flowType} is not a lifecycle initiated type
   */
  @PostMapping("/{flowType}/random-lifecycle")
  @Operation(
      summary = "Run N random-outcome lifecycles (unattended)",
      description =
          "Fires count distinct Settlement/Disbursement lifecycles on the backend, deciding "
              + "SUCCEED/FAIL at random per lifecycle. Returns a 202 run handle to poll.")
  public ResponseEntity<BatchRunResponse> randomLifecycle(
      @PathVariable FlowType flowType, @RequestBody PublishFlowRequest request) {

    FlowLifecycle lifecycle = resolveLifecycle(flowType);
    FlowRequest base = toFlowRequest(flowType, request);
    NTimesOptions options = lifecycleOptions(request);
    BatchRunResponse run = lifecycleRunService.startRun(base, lifecycle, options);
    return ResponseEntity.accepted().body(run);
  }

  /**
   * Runs a whole batch disbursement unattended (Phase 016): publishes one reservation, resolves the
   * {@code reservation_id}, splits the totals across N items, decides each item's outcome by the
   * {@link com.softspark.chaos.flow.dto.BatchOutcomePolicy}, and publishes per item
   * {@code item.request} then {@code item.completed|item.failed}. Always asynchronous: returns a
   * {@code 202} run handle (carrying {@code externalBatchId}) to poll in the run-results view.
   *
   * @param request the batch run request (reservation intent + N + split + outcome policy + pacing)
   * @return {@code 202} with a {@link BatchRunResponse} run handle
   */
  @PostMapping("/disbursement-batch/run")
  @Operation(
      summary = "Run an automatic batch disbursement (unattended)",
      description =
          "Publishes one reservation then N items (split + outcome policy) on the backend. "
              + "Returns a 202 run handle to poll, carrying the ledger batch_id for the summary.")
  public ResponseEntity<BatchRunResponse> runDisbursementBatch(
      @RequestBody BatchDisbursementRunRequest request) {
    BatchRunResponse run = batchDisbursementRunService.startRun(request);
    return ResponseEntity.accepted().body(run);
  }

  /** Resolves the lifecycle grouping for a flow type, or rejects a non-lifecycle type. */
  private FlowLifecycle resolveLifecycle(FlowType flowType) {
    return catalogProvider.catalog().stream()
        .filter(entry -> entry.flowType() == flowType && entry.lifecycle() != null)
        .map(FlowCatalogEntry::lifecycle)
        .findFirst()
        .orElseThrow(
            () ->
                new BadRequestException(
                    flowType
                        + " is not a lifecycle initiated flow type; use SETTLEMENT_INITIATED or "
                        + "DISBURSEMENT_INITIATED"));
  }

  /**
   * Reads {@code count}/pacing for a RANDOM lifecycle run from {@code chaos.nTimes} (reusing the
   * N-Times caps), defaulting to a single {@code BURST} lifecycle; execution mode is always ASYNC.
   */
  private NTimesOptions lifecycleOptions(PublishFlowRequest request) {
    NTimesOptions n = request.chaos() != null ? request.chaos().nTimes() : null;
    int count = n != null ? n.count() : 1;
    Pacing pacing = n != null && n.pacing() != null ? n.pacing() : Pacing.BURST;
    Long fixed = n != null ? n.fixedDelayMs() : null;
    Long min = n != null ? n.minDelayMs() : null;
    Long max = n != null ? n.maxDelayMs() : null;
    return new NTimesOptions(count, pacing, ExecutionMode.ASYNC, fixed, min, max);
  }

  private FlowRequest toFlowRequest(FlowType flowType, PublishFlowRequest request) {
    Map<String, String> slotOverrides =
        request.slotOverrides() != null ? request.slotOverrides() : Map.of();
    Map<String, Object> flowFields = request.flowFields() != null ? request.flowFields() : Map.of();

    return FlowRequestBuilder.builder()
        .flowType(flowType)
        .correlationId(request.correlationId())
        .tenantId(request.tenantId())
        .channel(request.channel())
        .amount(request.amount())
        .grossAmount(request.grossAmount())
        .netAmount(request.netAmount())
        .currency(request.currency())
        .slotOverrides(slotOverrides)
        .chaos(request.chaos())
        .flowFields(flowFields)
        .fees(request.fees())
        .build();
  }

  /**
   * Returns the full flow catalog with required fields, optional fields, and CSV column metadata.
   *
   * @return {@code 200} with the list of catalog entries
   */
  @GetMapping("/catalog")
  @Operation(
      summary = "Get the flow catalog",
      description = "Lists all supported flow types with their required and optional fields")
  public ResponseEntity<List<FlowCatalogEntry>> catalog() {
    return ResponseEntity.ok(catalogProvider.catalog());
  }
}
