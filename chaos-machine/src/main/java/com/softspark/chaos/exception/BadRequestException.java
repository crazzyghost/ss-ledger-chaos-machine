package com.softspark.chaos.exception;

/**
 * Exception thrown for bad request errors (400).
 */
public class BadRequestException extends HttpException {

  public BadRequestException(String message) {
    super(400, message);
  }

  public BadRequestException(String message, Throwable cause) {
    super(400, message, cause);
  }
}
