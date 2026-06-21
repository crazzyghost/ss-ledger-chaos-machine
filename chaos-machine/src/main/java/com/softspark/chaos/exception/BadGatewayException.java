package com.softspark.chaos.exception;

/**
 * Exception thrown when an upstream dependency (the ledger) returns a server error (502).
 */
public class BadGatewayException extends HttpException {

  public BadGatewayException(String message) {
    super(502, message);
  }

  public BadGatewayException(String message, Throwable cause) {
    super(502, message, cause);
  }
}
