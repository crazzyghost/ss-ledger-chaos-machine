package com.softspark.chaos.ledgerproxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.softspark.chaos.ledgerproxy.circuitbreaker.CircuitBreaker;
import com.softspark.chaos.ledgerproxy.circuitbreaker.CircuitBreakerOpenException;
import com.softspark.chaos.ledgerproxy.circuitbreaker.CircuitBreakerState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link CircuitBreaker}.
 */
@DisplayName("CircuitBreaker")
class CircuitBreakerTest {

  private CircuitBreaker breaker(int failThreshold, int successThreshold, long openDurationMs) {
    return new CircuitBreaker(failThreshold, successThreshold, openDurationMs);
  }

  @Nested
  @DisplayName("CLOSED state")
  class ClosedState {

    @Test
    @DisplayName("allows calls through and returns result")
    void allowsCallsThrough() {
      var cb = breaker(3, 2, 60_000);
      var result = cb.execute(() -> "ok");
      assertThat(result).isEqualTo("ok");
      assertThat(cb.getState()).isEqualTo(CircuitBreakerState.CLOSED);
    }

    @Test
    @DisplayName("opens circuit after failure threshold is reached")
    void opensAfterFailureThreshold() {
      var cb = breaker(3, 2, 60_000);
      for (int i = 0; i < 3; i++) {
        try {
          cb.execute(
              () -> {
                throw new RuntimeException("boom");
              });
        } catch (RuntimeException ignored) {
        }
      }
      assertThat(cb.getState()).isEqualTo(CircuitBreakerState.OPEN);
    }

    @Test
    @DisplayName("resets failure count on success")
    void resetsFailureCountOnSuccess() {
      var cb = breaker(3, 2, 60_000);
      // 2 failures then a success should keep it CLOSED
      for (int i = 0; i < 2; i++) {
        try {
          cb.execute(
              () -> {
                throw new RuntimeException("boom");
              });
        } catch (RuntimeException ignored) {
        }
      }
      cb.execute(() -> "ok");
      assertThat(cb.getState()).isEqualTo(CircuitBreakerState.CLOSED);
      // One more failure should NOT open it (count reset)
      try {
        cb.execute(
            () -> {
              throw new RuntimeException("boom");
            });
      } catch (RuntimeException ignored) {
      }
      assertThat(cb.getState()).isEqualTo(CircuitBreakerState.CLOSED);
    }
  }

  @Nested
  @DisplayName("OPEN state")
  class OpenState {

    @Test
    @DisplayName("throws CircuitBreakerOpenException immediately")
    void throwsOpenException() {
      var cb = breaker(1, 2, 60_000);
      try {
        cb.execute(
            () -> {
              throw new RuntimeException("boom");
            });
      } catch (RuntimeException ignored) {
      }
      assertThat(cb.getState()).isEqualTo(CircuitBreakerState.OPEN);

      assertThatThrownBy(() -> cb.execute(() -> "should not reach"))
          .isInstanceOf(CircuitBreakerOpenException.class);
    }

    @Test
    @DisplayName("transitions to HALF_OPEN after open duration elapses")
    void transitionsToHalfOpenAfterTimeout() {
      // Very short open duration so the test doesn't need to sleep long.
      var cb = breaker(1, 2, 1);
      try {
        cb.execute(
            () -> {
              throw new RuntimeException("boom");
            });
      } catch (RuntimeException ignored) {
      }
      assertThat(cb.getState()).isEqualTo(CircuitBreakerState.OPEN);

      // Allow the open duration to elapse
      try {
        Thread.sleep(5);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }

      // Next call should be allowed as a probe (HALF_OPEN)
      var result = cb.execute(() -> "probe");
      assertThat(result).isEqualTo("probe");
    }
  }

  @Nested
  @DisplayName("HALF_OPEN state")
  class HalfOpenState {

    @Test
    @DisplayName("closes circuit after success threshold met in HALF_OPEN")
    void closesAfterSuccessThreshold() throws InterruptedException {
      var cb = breaker(1, 2, 1);
      // Trip the breaker
      try {
        cb.execute(
            () -> {
              throw new RuntimeException("boom");
            });
      } catch (RuntimeException ignored) {
      }
      Thread.sleep(5);

      // Two successful probes should close the circuit
      cb.execute(() -> "probe1");
      cb.execute(() -> "probe2");
      assertThat(cb.getState()).isEqualTo(CircuitBreakerState.CLOSED);
    }

    @Test
    @DisplayName("re-opens circuit on probe failure")
    void reopensOnProbeFailure() throws InterruptedException {
      var cb = breaker(1, 2, 1);
      try {
        cb.execute(
            () -> {
              throw new RuntimeException("boom");
            });
      } catch (RuntimeException ignored) {
      }
      Thread.sleep(5);

      // Probe fails — should go back to OPEN
      try {
        cb.execute(
            () -> {
              throw new RuntimeException("probe fail");
            });
      } catch (RuntimeException ignored) {
      }
      assertThat(cb.getState()).isEqualTo(CircuitBreakerState.OPEN);
    }
  }
}
