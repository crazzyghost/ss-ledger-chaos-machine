package com.softspark.chaos.exception;

/**
 * Exception thrown when authentication fails (401).
 */
public class UnauthorizedException extends HttpException {

  public UnauthorizedException(String message) {
    super(401, message);
  }

  public UnauthorizedException(String message, Throwable cause) {
    super(401, message, cause);
  }
}
