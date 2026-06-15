package com.softspark.chaos.base;

import io.soabase.recordbuilder.core.RecordBuilder;
import java.util.List;

/**
 * Standard API error response record.
 * <p>
 * Returned by the global exception handler for all error responses. Includes a
 * request correlation ID for tracing, a human-readable message, and optional field-level errors.
 *
 * @param requestId      correlation identifier for request tracing
 * @param message        human-readable error message
 * @param errors         list of field-level validation errors (empty for non-validation errors)
 */
@RecordBuilder
public record ApiError(String requestId, String message, List<ErrorDescription> errors) {
  public ApiError {
    if (errors == null) {
      errors = List.of();
    }
  }
}
