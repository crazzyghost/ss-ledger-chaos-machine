package com.softspark.chaos.exception;

/**
 * Exception thrown when a requested resource is not found (404).
 */
public class NotFoundException extends HttpException {

  public NotFoundException(String message) {
    super(404, message);
  }

  public NotFoundException(String message, Throwable cause) {
    super(404, message, cause);
  }
}
