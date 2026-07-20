package com.softspark.chaos.flow;

import static org.assertj.core.api.Assertions.assertThat;

import com.softspark.chaos.flow.dto.BatchOutcomePolicy;
import com.softspark.chaos.flow.dto.BatchOutcomePolicy.Mode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link BatchOutcomePlan} — deterministic pass/fail patterns per outcome policy. */
@DisplayName("BatchOutcomePlan")
class BatchOutcomePlanTest {

  private final OutcomeDecider decider = new OutcomeDecider();

  private long countPasses(boolean[] pass) {
    int c = 0;
    for (boolean b : pass) {
      if (b) {
        c++;
      }
    }
    return c;
  }

  @Test
  void should_passAll_when_allPass() {
    boolean[] pass =
        BatchOutcomePlan.decide(new BatchOutcomePolicy(Mode.ALL_PASS, null, null), 5, decider, 1L);
    assertThat(countPasses(pass)).isEqualTo(5);
  }

  @Test
  void should_failAll_when_allFail() {
    boolean[] pass =
        BatchOutcomePlan.decide(new BatchOutcomePolicy(Mode.ALL_FAIL, null, null), 5, decider, 1L);
    assertThat(countPasses(pass)).isZero();
  }

  @Test
  void should_passFirstK_when_countMode() {
    boolean[] pass =
        BatchOutcomePlan.decide(new BatchOutcomePolicy(Mode.COUNT, 3, null), 5, decider, 1L);
    assertThat(pass).containsExactly(true, true, true, false, false);
  }

  @Test
  void should_hitTargetExactly_when_randomWithPassCount() {
    boolean[] pass =
        BatchOutcomePlan.decide(new BatchOutcomePolicy(Mode.RANDOM, 4, 42L), 10, decider, 42L);
    assertThat(countPasses(pass)).isEqualTo(4);
  }

  @Test
  void should_beDeterministic_when_randomBySeed() {
    var policy = new BatchOutcomePolicy(Mode.RANDOM, null, 7L);
    boolean[] a = BatchOutcomePlan.decide(policy, 20, decider, 7L);
    boolean[] b = BatchOutcomePlan.decide(policy, 20, decider, 7L);
    assertThat(a).isEqualTo(b);
  }
}
