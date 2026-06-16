package com.softspark.chaos.ledgerproxy.circuitbreaker;

/** State of the circuit breaker state machine. */
public enum CircuitBreakerState {
  /** Requests pass through normally. */
  CLOSED,
  /** Requests are short-circuited; a timed probe is allowed after the open duration expires. */
  OPEN,
  /** A single probe request is allowed to test whether the downstream has recovered. */
  HALF_OPEN
}
