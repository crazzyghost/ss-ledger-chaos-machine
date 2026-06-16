package com.softspark.chaos.account.bootstrap;

/**
 * Thrown when the ledger HTTP API returns an error or the request cannot be completed.
 *
 * <p>Carries the HTTP {@link #statusCode} so that callers can distinguish 4xx client errors (which
 * should not be retried) from 5xx server errors (which may be retried with back-off).
 *
 * <p>A {@code statusCode} of {@code 0} indicates a transport-level failure (connection timeout,
 * interrupted retry, or response parsing error) where no HTTP status was received.
 */
public class LedgerProvisioningException extends RuntimeException {

  private final int statusCode;

  /**
   * Constructs a new exception with the given message and no HTTP status code.
   *
   * @param message human-readable description of the failure
   */
  public LedgerProvisioningException(String message) {
    super(message);
    this.statusCode = 0;
  }

  /**
   * Constructs a new exception with the given message, chained cause, and no HTTP status code.
   *
   * @param message human-readable description of the failure
   * @param cause   the underlying cause
   */
  public LedgerProvisioningException(String message, Throwable cause) {
    super(message, cause);
    this.statusCode = 0;
  }

  /**
   * Constructs a new exception with the given message and HTTP status code.
   *
   * @param message    human-readable description of the failure
   * @param statusCode the HTTP status code returned by the ledger
   */
  public LedgerProvisioningException(String message, int statusCode) {
    super(message);
    this.statusCode = statusCode;
  }

  /**
   * Returns the HTTP status code received from the ledger, or {@code 0} for transport failures.
   *
   * @return HTTP status code, or {@code 0}
   */
  public int getStatusCode() {
    return statusCode;
  }
}
