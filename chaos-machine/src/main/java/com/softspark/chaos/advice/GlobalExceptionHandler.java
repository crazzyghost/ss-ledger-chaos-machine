package com.softspark.chaos.advice;

import com.softspark.chaos.base.ApiError;
import com.softspark.chaos.base.ErrorDescription;
import com.softspark.chaos.exception.HttpException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Global exception handler for all REST controllers.
 * <p>
 * Catches exceptions thrown by controllers and transforms them into standardized
 * {@link ApiError} responses with appropriate HTTP status codes.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final String REQUEST_ID_KEY = "requestId";

  @ExceptionHandler(HttpException.class)
  public ResponseEntity<ApiError> handleHttpException(
      HttpException ex, HttpServletRequest request) {
    String requestId = getRequestId();
    ApiError error = new ApiError(requestId, ex.getMessage(), List.of());
    return ResponseEntity.status(ex.getStatusCode()).body(error);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiError> handleValidationException(
      MethodArgumentNotValidException ex, HttpServletRequest request) {
    String requestId = getRequestId();
    List<ErrorDescription> errors =
        ex.getBindingResult().getFieldErrors().stream().map(this::toErrorDescription).toList();
    ApiError error = new ApiError(requestId, "Validation failed", errors);
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ApiError> handleIllegalArgumentException(
      IllegalArgumentException ex, HttpServletRequest request) {
    String requestId = getRequestId();
    ApiError error = new ApiError(requestId, ex.getMessage(), List.of());
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
  }

  @ExceptionHandler(IllegalStateException.class)
  public ResponseEntity<ApiError> handleIllegalStateException(
      IllegalStateException ex, HttpServletRequest request) {
    String requestId = getRequestId();
    ApiError error = new ApiError(requestId, ex.getMessage(), List.of());
    return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ApiError> handleAccessDeniedException(
      AccessDeniedException ex, HttpServletRequest request) {
    String requestId = getRequestId();
    ApiError error = new ApiError(requestId, "Access denied", List.of());
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiError> handleGenericException(Exception ex, HttpServletRequest request) {
    String requestId = getRequestId();
    ApiError error = new ApiError(requestId, "An internal error occurred", List.of());
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
  }

  private ErrorDescription toErrorDescription(FieldError fieldError) {
    return new ErrorDescription(
        fieldError.getField(),
        fieldError.getDefaultMessage() != null ? fieldError.getDefaultMessage() : "Invalid value");
  }

  private String getRequestId() {
    String requestId = MDC.get(REQUEST_ID_KEY);
    return requestId != null ? requestId : "unknown";
  }
}
