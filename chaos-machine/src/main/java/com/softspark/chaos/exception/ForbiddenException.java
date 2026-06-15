package com.softspark.chaos.exception;

/**
 * Exception thrown when access is denied (403).
 */
public class ForbiddenException extends HttpException {

  public ForbiddenException(String message) {
    super(403, message);
  }

  public ForbiddenException(String message, Throwable cause) {
    super(403, message, cause);
  }
}
