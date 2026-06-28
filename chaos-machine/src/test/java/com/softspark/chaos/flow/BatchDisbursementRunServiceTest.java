package com.softspark.chaos.flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.softspark.chaos.batch.dto.BatchRunResponse;
import com.softspark.chaos.batch.enumeration.RunKind;
import com.softspark.chaos.batch.repository.BatchRowRepository;
import com.softspark.chaos.batch.repository.BatchRunRepository;
import com.softspark.chaos.batch.service.BatchRunner;
import com.softspark.chaos.exception.BadRequestException;
import com.softspark.chaos.flow.chaos.ChaosLimits;
import com.softspark.chaos.flow.dto.BatchDisbursementRunRequestBuilder;
import com.softspark.chaos.flow.dto.BatchOutcomePolicy;
import com.softspark.chaos.flow.dto.BatchOutcomePolicy.Mode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit tests for {@link BatchDisbursementRunService} — validation + run materialization. */
@ExtendWith(MockitoExtension.class)
@DisplayName("BatchDisbursementRunService")
class BatchDisbursementRunServiceTest {

  @Mock private BatchRunRepository batchRunRepository;
  @Mock private BatchRowRepository batchRowRepository;
  @Mock private BatchRunner batchRunner;
  @Mock private BatchDisbursementRunner batchDisbursementRunner;

  private final OutcomeDecider outcomeDecider = new OutcomeDecider();
  private final ChaosLimits limits = new ChaosLimits(10, 100, 1000, 30000L, 250, 25, 60000L, 100);

  private BatchDisbursementRunService service() {
    return new BatchDisbursementRunService(
        batchRunRepository,
        batchRowRepository,
        batchRunner,
        batchDisbursementRunner,
        outcomeDecider,
        limits);
  }

  private BatchDisbursementRunRequestBuilder base() {
    return BatchDisbursementRunRequestBuilder.builder()
        .sourceVaId("VA-ORG")
        .destinationVaId("VA-FLOAT")
        .merchantId("ORG-1")
        .itemCount(4)
        .outcomePolicy(new BatchOutcomePolicy(Mode.ALL_PASS, null, null));
  }

  @Test
  void should_reject_when_itemCountOverCap() {
    var request = base().itemCount(101).build();
    assertThatThrownBy(() -> service().startRun(request)).isInstanceOf(BadRequestException.class);
    verifyNoInteractions(batchRunner);
  }

  @Test
  void should_reject_when_itemCountZero() {
    var request = base().itemCount(0).build();
    assertThatThrownBy(() -> service().startRun(request)).isInstanceOf(BadRequestException.class);
  }

  @Test
  void should_reject_when_passCountOutOfRange() {
    var request = base().outcomePolicy(new BatchOutcomePolicy(Mode.COUNT, 9, null)).build();
    assertThatThrownBy(() -> service().startRun(request)).isInstanceOf(BadRequestException.class);
  }

  @Test
  void should_reject_when_countModeMissingPassCount() {
    var request = base().outcomePolicy(new BatchOutcomePolicy(Mode.COUNT, null, null)).build();
    assertThatThrownBy(() -> service().startRun(request)).isInstanceOf(BadRequestException.class);
  }

  @Test
  void should_reject_when_sourceVaBlank() {
    var request = base().sourceVaId(null).build();
    assertThatThrownBy(() -> service().startRun(request)).isInstanceOf(BadRequestException.class);
  }

  @Test
  void should_startRun_when_valid() {
    var request = base().itemCount(3).build();

    BatchRunResponse run = service().startRun(request);

    assertThat(run.kind()).isEqualTo(RunKind.BATCH_DISBURSEMENT);
    assertThat(run.total()).isEqualTo(3);
    assertThat(run.externalBatchId()).isNotBlank();
    verify(batchRunner)
        .executeBatchDisbursement(
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.eq(3),
            org.mockito.ArgumentMatchers.any());
  }
}
