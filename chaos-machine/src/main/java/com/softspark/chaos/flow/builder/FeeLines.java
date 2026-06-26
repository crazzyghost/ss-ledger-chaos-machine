package com.softspark.chaos.flow.builder;

import com.softspark.chaos.base.Ids;
import com.softspark.chaos.flow.dto.FeeInput;
import com.softspark.chaos.flow.model.v1.TransactionFeeLine;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.lang.Nullable;

/**
 * Package-private helper mapping operator-supplied {@link FeeInput} rows to wire
 * {@link TransactionFeeLine}s, shared by the collection and disbursement-completed builders.
 *
 * <p>Per row: a blank {@code feeType} falls back to the per-flow default; a blank {@code feeCode} is
 * minted as a ULID server-side (the ledger requires a non-blank code); a null {@code amount}
 * collapses to {@link BigDecimal#ZERO}; a null {@code destinationVaId} collapses to an empty string.
 */
final class FeeLines {

  private FeeLines() {}

  /**
   * Maps the request fee rows to wire fee lines, applying the default fee type and autogenerating
   * blank fee codes.
   *
   * @param inputs the request fee rows (may be null/empty)
   * @param defaultFeeType the per-flow default {@code fee_type} applied to rows that omit one
   * @return an immutable list of fee lines (empty when {@code inputs} is null/empty)
   */
  static List<TransactionFeeLine> from(@Nullable List<FeeInput> inputs, String defaultFeeType) {
    if (inputs == null || inputs.isEmpty()) {
      return List.of();
    }
    List<TransactionFeeLine> lines = new ArrayList<>(inputs.size());
    for (FeeInput in : inputs) {
      String feeType =
          in.feeType() != null && !in.feeType().isBlank() ? in.feeType() : defaultFeeType;
      BigDecimal amount = in.amount() != null ? in.amount() : BigDecimal.ZERO;
      String feeCode =
          in.feeCode() != null && !in.feeCode().isBlank() ? in.feeCode() : Ids.generateULID();
      String destinationVaId = in.destinationVaId() != null ? in.destinationVaId() : "";
      lines.add(new TransactionFeeLine(feeType, amount, feeCode, destinationVaId));
    }
    return List.copyOf(lines);
  }

  /**
   * Sums the amounts of the given fee lines.
   *
   * @param lines the fee lines
   * @return the total fee amount ({@link BigDecimal#ZERO} for an empty list)
   */
  static BigDecimal sum(List<TransactionFeeLine> lines) {
    return lines.stream()
        .map(TransactionFeeLine::amount)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }
}
