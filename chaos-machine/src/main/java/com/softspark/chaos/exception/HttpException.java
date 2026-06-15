package com.softspark.chaos.exception;

/**
 * Base exception for all HTTP-mapped exceptions in the chaos machine.
 * <p>
 * Subclasses define specific HTTP status codes and are handled by the global exception handler.
 */
public abstract class HttpException extends RuntimeException {

  private final int statusCode;

  protected HttpException(int statusCode, String message) {
    super(message);
    this.statusCode = statusCode;
  }

  protected HttpException(int statusCode, String message, Throwable cause) {
    super(message, cause);
    this.statusCode = statusCode;
  }

  public int getStatusCode() {
    return statusCode;
  }
}
