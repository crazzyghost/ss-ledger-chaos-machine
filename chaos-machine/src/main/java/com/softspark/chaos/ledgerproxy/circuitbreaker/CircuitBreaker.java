package com.softspark.chaos.ledgerproxy.circuitbreaker;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Minimal atomic circuit breaker guarding outbound calls to the ledger service.
 *
 * <p>State transitions:
 * <ul>
 *   <li>{@link CircuitBreakerState#CLOSED} → {@link CircuitBreakerState#OPEN} when consecutive
 *       failures reach {@code failureThreshold}.</li>
 *   <li>{@link CircuitBreakerState#OPEN} → {@link CircuitBreakerState#HALF_OPEN} after
 *       {@code openDurationMs} elapses; one probe request is then allowed.</li>
 *   <li>{@link CircuitBreakerState#HALF_OPEN} → {@link CircuitBreakerState#CLOSED} on probe
 *       success; back to {@link CircuitBreakerState#OPEN} on probe failure.</li>
 * </ul>
 *
 * <p>Thread-safe; uses atomic variables without locking.
 */
public class CircuitBreaker {

  private final int failureThreshold;
  private final int successThreshold;
  private final long openDurationMs;

  private final AtomicReference<CircuitBreakerState> state =
      new AtomicReference<>(CircuitBreakerState.CLOSED);
  private final AtomicInteger failureCount = new AtomicInteger(0);
  private final AtomicInteger successCount = new AtomicInteger(0);
  private final AtomicLong lastOpenedAt = new AtomicLong(0);

  /**
   * Constructs a circuit breaker with the specified thresholds.
   *
   * @param failureThreshold consecutive failures before opening (default 5)
   * @param successThreshold consecutive successes in HALF_OPEN before closing (default 2)
   * @param openDurationMs milliseconds to remain OPEN before transitioning to HALF_OPEN (default
   *     30000)
   */
  public CircuitBreaker(int failureThreshold, int successThreshold, long openDurationMs) {
    this.failureThreshold = failureThreshold > 0 ? failureThreshold : 5;
    this.successThreshold = successThreshold > 0 ? successThreshold : 2;
    this.openDurationMs = openDurationMs > 0 ? openDurationMs : 30_000;
  }

  /**
   * Executes the supplied action within the circuit breaker.
   *
   * @param <T> the return type
   * @param action the action to execute
   * @return the result of the action
   * @throws CircuitBreakerOpenException if the circuit is open and the reset timeout has not
   *     elapsed
   */
  public <T> T execute(Supplier<T> action) {
    var current = state.get();

    switch (current) {
      case OPEN -> {
        if (System.currentTimeMillis() - lastOpenedAt.get() >= openDurationMs) {
          // Transition to HALF_OPEN and allow one probe.
          state.compareAndSet(CircuitBreakerState.OPEN, CircuitBreakerState.HALF_OPEN);
          successCount.set(0);
        } else {
          throw new CircuitBreakerOpenException(
              "Circuit breaker is OPEN — ledger service temporarily unavailable");
        }
      }
      default -> {
        // CLOSED or HALF_OPEN: fall through to attempt the call.
      }
    }

    try {
      T result = action.get();
      onSuccess();
      return result;
    } catch (CircuitBreakerOpenException e) {
      throw e;
    } catch (Exception e) {
      onFailure();
      throw e;
    }
  }

  /** Returns the current circuit breaker state. */
  public CircuitBreakerState getState() {
    return state.get();
  }

  private void onSuccess() {
    var current = state.get();
    if (current == CircuitBreakerState.HALF_OPEN) {
      int count = successCount.incrementAndGet();
      if (count >= successThreshold) {
        state.set(CircuitBreakerState.CLOSED);
        failureCount.set(0);
        successCount.set(0);
      }
    } else {
      failureCount.set(0);
    }
  }

  private void onFailure() {
    var current = state.get();
    if (current == CircuitBreakerState.HALF_OPEN) {
      lastOpenedAt.set(System.currentTimeMillis());
      state.set(CircuitBreakerState.OPEN);
      successCount.set(0);
    } else {
      int count = failureCount.incrementAndGet();
      if (count >= failureThreshold) {
        lastOpenedAt.set(System.currentTimeMillis());
        state.set(CircuitBreakerState.OPEN);
        failureCount.set(0);
      }
    }
  }
}
