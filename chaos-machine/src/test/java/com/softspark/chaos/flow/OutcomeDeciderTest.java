package com.softspark.chaos.flow;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link OutcomeDecider} (deterministic, resume-safe outcomes). */
@DisplayName("OutcomeDecider")
class OutcomeDeciderTest {

  private final OutcomeDecider decider = new OutcomeDecider();

  @Test
  void should_beDeterministic_when_sameSeedAndIndex() {
    assertThat(decider.succeeds(123L, 7)).isEqualTo(decider.succeeds(123L, 7));
    assertThat(decider.succeeds(123L, 7)).isEqualTo(decider.succeeds(123L, 7));
  }

  @Test
  void should_produceBothOutcomes_when_acrossIndices() {
    long seed = decider.seedFor("run-abc");
    var outcomes = IntStream.range(0, 50).mapToObj(i -> decider.succeeds(seed, i)).toList();
    assertThat(outcomes).contains(Boolean.TRUE).contains(Boolean.FALSE);
  }

  @Test
  void should_deriveStableDistinctSeed_when_perRunId() {
    assertThat(decider.seedFor("run-1")).isEqualTo(decider.seedFor("run-1"));
    assertThat(decider.seedFor("run-1")).isNotEqualTo(decider.seedFor("run-2"));
  }
}
