package com.softspark.chaos.flow;

import com.softspark.chaos.base.Ids;
import com.softspark.chaos.batch.dto.BatchRunResponse;
import com.softspark.chaos.batch.enumeration.BatchRowStatus;
import com.softspark.chaos.batch.enumeration.BatchRunStatus;
import com.softspark.chaos.batch.enumeration.RunKind;
import com.softspark.chaos.batch.model.BatchRow;
import com.softspark.chaos.batch.model.BatchRun;
import com.softspark.chaos.batch.repository.BatchRowRepository;
import com.softspark.chaos.batch.repository.BatchRunRepository;
import com.softspark.chaos.batch.service.BatchRunner;
import com.softspark.chaos.batch.service.PacingPlan;
import com.softspark.chaos.exception.BadRequestException;
import com.softspark.chaos.flow.chaos.ChaosLimits;
import com.softspark.chaos.flow.chaos.ExecutionMode;
import com.softspark.chaos.flow.chaos.NTimesOptions;
import com.softspark.chaos.flow.chaos.Pacing;
import com.softspark.chaos.flow.dto.BatchDisbursementRunRequest;
import com.softspark.chaos.flow.dto.BatchOutcomePolicy;
import com.softspark.chaos.flow.model.FlowType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Materializes an automatic batch-disbursement request as a tracked run and hands it to the
 * {@link BatchRunner} for background execution (ADR-022 §4).
 *
 * <p>Mirrors {@link LifecycleRunService}: it validates against {@link ChaosLimits#maxBatchItems()}
 * (and the outcome {@code passCount} into {@code [0, N]}), mints the batch identity, computes the
 * even split ({@link BatchSplit}) and the per-item outcomes ({@link BatchOutcomePlan}), persists the
 * run ({@link RunKind#BATCH_DISBURSEMENT}, {@code external_batch_id = batch_id}) + N {@code PENDING}
 * rows, then submits a resume-safe {@link BatchDisbursementRunner.Plan} to the runner. The request
 * returns in O(1) with a run handle; progress is polled through the existing run endpoints and the
 * ledger batch-summary read-proxy.
 */
@Service
public class BatchDisbursementRunService {

  private static final Logger log = LoggerFactory.getLogger(BatchDisbursementRunService.class);

  /** The decimal scale used when splitting the totals (matches the {@code 1000.0000} convention). */
  private static final int SPLIT_SCALE = 4;

  private static final BigDecimal DEFAULT_PRINCIPAL = new BigDecimal("1000.0000");
  private static final BigDecimal DEFAULT_FEES = new BigDecimal("10");
  private static final String DEFAULT_CURRENCY = "GHS";
  private static final String DEFAULT_SUBTYPE = "DOMESTIC";
  private static final String DEFAULT_PROVIDER = "PROVIDER_GH";
  private static final String DEFAULT_CREDIT_ACCOUNT = "0240000000";
  private static final String DEFAULT_AUTH_USER = "chaos-operator";
  private static final String DEFAULT_AUTH_FINGERPRINT = "ab:cd:ef:00";
  private static final String DEFAULT_FAILURE_REASON = "Batch item disbursement failed";
  private static final String DEFAULT_FAILURE_CODE = "RECIPIENT_INVALID";

  private final BatchRunRepository batchRunRepository;
  private final BatchRowRepository batchRowRepository;
  private final BatchRunner batchRunner;
  private final BatchDisbursementRunner batchDisbursementRunner;
  private final OutcomeDecider outcomeDecider;
  private final ChaosLimits limits;

  public BatchDisbursementRunService(
      BatchRunRepository batchRunRepository,
      BatchRowRepository batchRowRepository,
      BatchRunner batchRunner,
      BatchDisbursementRunner batchDisbursementRunner,
      OutcomeDecider outcomeDecider,
      ChaosLimits limits) {
    this.batchRunRepository = batchRunRepository;
    this.batchRowRepository = batchRowRepository;
    this.batchRunner = batchRunner;
    this.batchDisbursementRunner = batchDisbursementRunner;
    this.outcomeDecider = outcomeDecider;
    this.limits = limits;
  }

  /**
   * Validates and starts a tracked automatic batch-disbursement run.
   *
   * @param request the run request (reservation intent + N + split + outcome policy + pacing/chaos)
   * @return the created run handle (status = RUNNING, {@code externalBatchId} set)
   * @throws BadRequestException if N is out of {@code [1, maxBatchItems]}, the source/destination/
   *     merchant ids are blank, or the outcome {@code passCount} is invalid
   */
  @Transactional
  public BatchRunResponse startRun(BatchDisbursementRunRequest request) {
    int n = request.itemCount();
    if (n < 1 || n > limits.maxBatchItems()) {
      throw new BadRequestException(
          "itemCount must be in [1, " + limits.maxBatchItems() + "], was " + n);
    }
    String sourceVaId = required(request.sourceVaId(), "sourceVaId");
    String destinationVaId = required(request.destinationVaId(), "destinationVaId");
    String merchantId = required(request.merchantId(), "merchantId");
    BatchOutcomePolicy policy = resolvePolicy(request.outcomePolicy(), n);

    BigDecimal totalPrincipal =
        request.totalPrincipalAmount() != null ? request.totalPrincipalAmount() : DEFAULT_PRINCIPAL;
    BigDecimal totalFees = request.totalFees() != null ? request.totalFees() : DEFAULT_FEES;

    String runId = Ids.generate();
    String batchId = Ids.generateUUID();
    long seed = policy.seed() != null ? policy.seed() : outcomeDecider.seedFor(runId);
    boolean[] outcomes = BatchOutcomePlan.decide(policy, n, outcomeDecider, seed);
    List<BatchSplit.ItemAmount> split =
        BatchSplit.even(totalPrincipal, totalFees, n, SPLIT_SCALE);

    List<BatchDisbursementRunner.Item> items = new ArrayList<>(n);
    for (int i = 0; i < n; i++) {
      items.add(
          new BatchDisbursementRunner.Item(
              split.get(i).principal(), split.get(i).fee(), outcomes[i]));
    }

    NTimesOptions pacingOptions = pacingOptions(request.pacing(), n);

    var run = new BatchRun();
    run.setId(runId);
    run.setFlowType(FlowType.DISBURSEMENT_BATCH_RESERVATION_REQUEST.name());
    run.setFilename(null);
    run.setKind(RunKind.BATCH_DISBURSEMENT);
    run.setPacing(pacingOptions.pacing().name());
    run.setMode(ExecutionMode.ASYNC.name());
    run.setTotal(n);
    run.setStatus(BatchRunStatus.RUNNING);
    run.setCreatedAt(Instant.now());
    run.setExternalBatchId(batchId);
    batchRunRepository.save(run);

    List<BatchRunner.BatchDisbursementUnit> units = new ArrayList<>(n);
    for (int i = 0; i < n; i++) {
      var row = new BatchRow();
      row.setId(Ids.generate());
      row.setBatchId(runId);
      row.setRowNumber(i);
      row.setStatus(BatchRowStatus.PENDING);
      row.setCreatedAt(Instant.now());
      batchRowRepository.save(row);
      units.add(new BatchRunner.BatchDisbursementUnit(row, i));
    }

    var plan =
        new BatchDisbursementRunner.Plan(
            "",
            request.tenantId(),
            blankToDefault(request.correlationId(), Ids.generate()),
            batchId,
            Ids.generateUUID(),
            merchantId,
            sourceVaId,
            destinationVaId,
            blankToDefault(request.currency(), DEFAULT_CURRENCY),
            totalPrincipal,
            totalFees,
            n,
            blankToDefault(request.disbursementSubtype(), DEFAULT_SUBTYPE),
            blankToDefault(request.merchantBatchRef(), Ids.generateULID()),
            request.callbackUrl(),
            blankToDefault(request.authorisedUserId(), DEFAULT_AUTH_USER),
            blankToDefault(request.authorisedKeyFingerprint(), DEFAULT_AUTH_FINGERPRINT),
            blankToDefault(request.creditProviderId(), DEFAULT_PROVIDER),
            blankToDefault(request.creditAccountId(), DEFAULT_CREDIT_ACCOUNT),
            blankToDefault(request.providerId(), DEFAULT_PROVIDER),
            request.sourceCountry(),
            request.destinationCountry(),
            request.feeVaId(),
            blankToDefault(request.failureReason(), DEFAULT_FAILURE_REASON),
            blankToDefault(request.failureCode(), DEFAULT_FAILURE_CODE),
            request.chaos(),
            items);

    log.info(
        "Starting batch-disbursement run {} (batch {}, {} items, policy={}, pacing={})",
        runId,
        batchId,
        n,
        policy.mode(),
        pacingOptions.pacing());

    batchRunner.executeBatchDisbursement(
        runId, plan, units, PacingPlan.forOptions(pacingOptions), n, batchDisbursementRunner);

    return BatchRunResponse.from(run);
  }

  /** Validates the outcome policy and its {@code passCount} into {@code [0, N]}. */
  private BatchOutcomePolicy resolvePolicy(BatchOutcomePolicy policy, int n) {
    if (policy == null) {
      throw new BadRequestException("outcomePolicy is required");
    }
    Integer passCount = policy.passCount();
    if (policy.mode() == BatchOutcomePolicy.Mode.COUNT && passCount == null) {
      throw new BadRequestException("outcomePolicy.passCount is required for mode COUNT");
    }
    if (passCount != null && (passCount < 0 || passCount > n)) {
      throw new BadRequestException("outcomePolicy.passCount must be in [0, " + n + "]");
    }
    return policy;
  }

  /**
   * Builds the pacing for the item loop from the optional N-Times-style pacing (its {@code count} is
   * ignored — N drives the item count); defaults to a single {@code BURST} fan-out.
   */
  private NTimesOptions pacingOptions(NTimesOptions pacing, int n) {
    Pacing mode = pacing != null && pacing.pacing() != null ? pacing.pacing() : Pacing.BURST;
    Long fixed = pacing != null ? pacing.fixedDelayMs() : null;
    Long min = pacing != null ? pacing.minDelayMs() : null;
    Long max = pacing != null ? pacing.maxDelayMs() : null;
    if (mode == Pacing.LINEAR && fixed == null) {
      fixed = 0L;
    }
    if (mode == Pacing.RANDOM) {
      if (min == null) {
        min = 0L;
      }
      if (max == null) {
        max = min;
      }
    }
    return new NTimesOptions(n, mode, ExecutionMode.ASYNC, fixed, min, max);
  }

  private static String required(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new BadRequestException(name + " is required");
    }
    return value;
  }

  private static String blankToDefault(String value, String fallback) {
    return value != null && !value.isBlank() ? value : fallback;
  }
}
