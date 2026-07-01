package com.softspark.chaos.advice;

import com.softspark.chaos.base.ApiError;
import com.softspark.chaos.base.ErrorDescription;
import com.softspark.chaos.exception.HttpException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

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

  /**
   * Handles {@link ConstraintViolationException} thrown when method-parameter or path-variable
   * constraints are violated. Returns 400 with field-level error details.
   *
   * @param ex      the constraint violation exception
   * @param request the current HTTP request
   * @return a 400 Bad Request response containing an {@link ApiError}
   */
  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ApiError> handleConstraintViolation(
      ConstraintViolationException ex, HttpServletRequest request) {
    String requestId = getRequestId();
    List<ErrorDescription> errors =
        ex.getConstraintViolations().stream()
            .map(cv -> new ErrorDescription(cv.getPropertyPath().toString(), cv.getMessage()))
            .toList();
    ApiError error = new ApiError(requestId, "Validation failed", errors);
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
  }

  /**
   * Handles {@link MissingServletRequestParameterException} thrown when a required {@code
   * @RequestParam} is absent (e.g. the trial-balance endpoint called without {@code from}/{@code
   * to}). Returns 400 so a missing-parameter client error is not swallowed by the generic 500
   * handler below — matching standard Spring MVC semantics.
   *
   * @param ex the missing-parameter exception
   * @param request the current HTTP request
   * @return a 400 Bad Request response containing an {@link ApiError}
   */
  @ExceptionHandler(MissingServletRequestParameterException.class)
  public ResponseEntity<ApiError> handleMissingRequestParameter(
      MissingServletRequestParameterException ex, HttpServletRequest request) {
    String requestId = getRequestId();
    ApiError error =
        new ApiError(
            requestId,
            ex.getMessage(),
            List.of(new ErrorDescription(ex.getParameterName(), "Required parameter is missing")));
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

  /**
   * Handles {@link NoResourceFoundException} thrown when no handler matches the request path (e.g. a
   * retired endpoint). Returns 404 — standard REST semantics — rather than letting the generic 500
   * handler below swallow it. Without this, an unmapped path would surface as a {@code 500}.
   *
   * @param ex the no-resource exception
   * @param request the current HTTP request
   * @return a 404 Not Found response containing an {@link ApiError}
   */
  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<ApiError> handleNoResourceFound(
      NoResourceFoundException ex, HttpServletRequest request) {
    String requestId = getRequestId();
    ApiError error = new ApiError(requestId, "Resource not found", List.of());
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
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
