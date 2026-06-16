package com.softspark.chaos.flow.builder;

import com.softspark.chaos.exception.BadRequestException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import org.springframework.lang.Nullable;

/**
 * Package-private utility for reading typed values from a flow request's {@code flowFields} map.
 *
 * <p>All {@code getRequired*} methods throw {@link BadRequestException} when the field is absent or
 * blank. All {@code getOptional*} methods return {@code null} when absent.
 */
class FlowFields {

  private final Map<String, Object> fields;

  FlowFields(Map<String, Object> fields) {
    this.fields = fields;
  }

  /**
   * Returns the value for the given key as a non-blank string.
   *
   * @param key the field key (snake_case)
   * @return the string value
   * @throws BadRequestException if the field is missing or blank
   */
  String getRequired(String key) {
    Object value = fields.get(key);
    if (value == null || value.toString().isBlank()) {
      throw new BadRequestException("Required flow field missing: " + key);
    }
    return value.toString();
  }

  /**
   * Returns the value for the given key as a string, or {@code null} if absent.
   *
   * @param key the field key
   * @return the string value or {@code null}
   */
  @Nullable
  String getOptional(String key) {
    Object value = fields.get(key);
    return value != null ? value.toString() : null;
  }

  /**
   * Returns the value for the given key as a {@link BigDecimal}, or {@code null} if absent.
   *
   * @param key the field key
   * @return the BigDecimal value or {@code null}
   */
  @Nullable
  BigDecimal getBigDecimal(String key) {
    Object value = fields.get(key);
    if (value == null) {
      return null;
    }
    if (value instanceof BigDecimal bd) {
      return bd;
    }
    try {
      return new BigDecimal(value.toString());
    } catch (NumberFormatException e) {
      throw new BadRequestException("Invalid numeric value for field " + key + ": " + value);
    }
  }

  /**
   * Returns the value for the given key as an ISO-8601 timestamp string, defaulting to
   * {@link Instant#now()} if absent.
   *
   * @param key the field key
   * @return the timestamp string
   */
  String getTimestampOrNow(String key) {
    Object value = fields.get(key);
    if (value == null || value.toString().isBlank()) {
      return Instant.now().toString();
    }
    return value.toString();
  }
}
