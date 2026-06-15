package com.softspark.chaos.config;

import com.softspark.chaos.base.Ids;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Filter that assigns a correlation ID to each request.
 * <p>
 * If the client provides an X-Request-ID header, it is used. Otherwise, a new ULID is generated.
 * The request ID is added to the MDC for logging and included in all error responses.
 */
@Component
public class RequestCorrelationFilter extends OncePerRequestFilter {

  private static final String REQUEST_ID_HEADER = "X-Request-ID";
  private static final String REQUEST_ID_KEY = "requestId";

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String requestId = request.getHeader(REQUEST_ID_HEADER);
    if (requestId == null || requestId.isBlank()) {
      requestId = Ids.generate();
    }
    MDC.put(REQUEST_ID_KEY, requestId);
    response.setHeader(REQUEST_ID_HEADER, requestId);
    try {
      filterChain.doFilter(request, response);
    } finally {
      MDC.remove(REQUEST_ID_KEY);
    }
  }
}
