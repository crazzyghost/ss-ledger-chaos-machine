package com.softspark.chaos.batch.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.softspark.chaos.flow.chaos.ExecutionMode;
import com.softspark.chaos.flow.chaos.NTimesOptions;
import com.softspark.chaos.flow.chaos.Pacing;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link PacingPlan}. */
@DisplayName("PacingPlan")
class PacingPlanTest {

  @Test
  @DisplayName("BURST → wide concurrency, zero delay")
  void burst() {
    var plan =
        PacingPlan.forOptions(
            new NTimesOptions(5, Pacing.BURST, ExecutionMode.ASYNC, null, null, null));
    assertThat(plan.concurrency()).isEqualTo(Integer.MAX_VALUE);
    assertThat(plan.delayMs().getAsLong()).isZero();
  }

  @Test
  @DisplayName("LINEAR → single-threaded, fixed delay")
  void linear() {
    var plan =
        PacingPlan.forOptions(
            new NTimesOptions(5, Pacing.LINEAR, ExecutionMode.ASYNC, 250L, null, null));
    assertThat(plan.concurrency()).isEqualTo(1);
    assertThat(plan.delayMs().getAsLong()).isEqualTo(250L);
  }

  @Test
  @DisplayName("RANDOM → single-threaded, delay within [min, max]")
  void random() {
    var plan =
        PacingPlan.forOptions(
            new NTimesOptions(5, Pacing.RANDOM, ExecutionMode.ASYNC, null, 100L, 300L));
    assertThat(plan.concurrency()).isEqualTo(1);
    for (int i = 0; i < 50; i++) {
      assertThat(plan.delayMs().getAsLong()).isBetween(100L, 300L);
    }
  }

  @Test
  @DisplayName("RANDOM with min == max is degenerate")
  void randomDegenerate() {
    var plan =
        PacingPlan.forOptions(
            new NTimesOptions(5, Pacing.RANDOM, ExecutionMode.ASYNC, null, 200L, 200L));
    assertThat(plan.delayMs().getAsLong()).isEqualTo(200L);
  }
}
