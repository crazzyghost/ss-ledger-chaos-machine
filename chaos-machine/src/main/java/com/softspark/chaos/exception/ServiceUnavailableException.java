package com.softspark.chaos.exception;

/**
 * Exception thrown when an upstream dependency (the ledger) is unreachable or its circuit is open
 * (503).
 */
public class ServiceUnavailableException extends HttpException {

  public ServiceUnavailableException(String message) {
    super(503, message);
  }

  public ServiceUnavailableException(String message, Throwable cause) {
    super(503, message, cause);
  }
}
