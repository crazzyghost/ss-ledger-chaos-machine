package com.softspark.chaos.exception;

/**
 * Exception thrown for conflict errors (409).
 * <p>
 * Typically used for domain-level business rule violations or state conflicts.
 */
public class ConflictException extends HttpException {

  public ConflictException(String message) {
    super(409, message);
  }

  public ConflictException(String message, Throwable cause) {
    super(409, message, cause);
  }
}
