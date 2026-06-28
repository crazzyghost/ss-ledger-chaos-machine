package com.softspark.chaos.flow.builder;

import com.softspark.chaos.exception.BadRequestException;
import java.math.BigDecimal;
import org.springframework.lang.Nullable;

/**
 * Package-private helpers shared by the batch-disbursement item builders for reading the per-item
 * amounts, sequence, and defaulted strings out of {@code flowFields}.
 *
 * <p>Amounts collapse to {@link BigDecimal#ZERO} when absent so a deliberately-unbalanced or minimal
 * item still produces a valid (sendable) envelope; {@code item_sequence} defaults to {@code 1} when a
 * caller omits it (the runner/wizard always supply a 1-based value).
 */
final class BatchFields {

  private BatchFields() {}

  /** Reads a decimal {@code flowFields} value, defaulting to {@link BigDecimal#ZERO} when absent. */
  static BigDecimal amount(FlowFields f, String key) {
    BigDecimal value = f.getBigDecimal(key);
    return value != null ? value : BigDecimal.ZERO;
  }

  /** Reads the 1-based {@code item_sequence}, defaulting to {@code 1} when absent. */
  static int itemSequence(FlowFields f) {
    String raw = f.getOptional("item_sequence");
    if (raw == null || raw.isBlank()) {
      return 1;
    }
    try {
      return Integer.parseInt(raw.trim());
    } catch (NumberFormatException e) {
      throw new BadRequestException("Invalid item_sequence: " + raw);
    }
  }

  /** Returns {@code value} when non-blank, else {@code fallback}. */
  static String defaulted(@Nullable String value, String fallback) {
    return value != null && !value.isBlank() ? value : fallback;
  }
}
