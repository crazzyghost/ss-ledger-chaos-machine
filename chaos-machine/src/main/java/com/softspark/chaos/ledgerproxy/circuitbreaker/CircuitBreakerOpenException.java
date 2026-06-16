package com.softspark.chaos.ledgerproxy.circuitbreaker;

/**
 * Thrown when the circuit breaker is in the {@link CircuitBreakerState#OPEN} state and a call
 * is attempted before the reset timeout has elapsed.
 */
public class CircuitBreakerOpenException extends RuntimeException {

  /**
   * Constructs the exception.
   *
   * @param message a descriptive message
   */
  public CircuitBreakerOpenException(String message) {
    super(message);
  }
}
