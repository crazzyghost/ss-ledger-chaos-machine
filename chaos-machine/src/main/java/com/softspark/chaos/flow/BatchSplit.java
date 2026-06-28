package com.softspark.chaos.flow;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure helper that splits a batch's {@code total_principal_amount} and {@code total_fees} evenly
 * across N items at a given decimal scale, with the <strong>last</strong> item absorbing the rounding
 * remainder so {@code Σ principal == total_principal} and {@code Σ fee == total_fees} exactly (hence
 * {@code Σ (principal + fee) == total_amount}).
 *
 * <p>This preserves the ledger's amount invariant for an even split so the BATCH reservation fully
 * captures/releases. Operators may still deliberately break the invariant via manual edits — an
 * observable chaos condition the ledger does not enforce on item sums.
 */
public final class BatchSplit {

  private BatchSplit() {}

  /**
   * One item's principal and fee slice.
   *
   * @param principal the item's principal slice
   * @param fee the item's fee slice
   */
  public record ItemAmount(BigDecimal principal, BigDecimal fee) {}

  /**
   * Splits the totals evenly across {@code n} items, absorbing the remainder in the last item.
   *
   * @param totalPrincipal the total principal across all items
   * @param totalFees the total fees across all items
   * @param n the number of items (must be &gt;= 1)
   * @param scale the decimal scale to divide at (e.g. {@code 4} for {@code 1000.0000})
   * @return the per-item amounts; the last item carries the remainder so the slices sum to the totals
   * @throws IllegalArgumentException if {@code n < 1}
   */
  public static List<ItemAmount> even(
      BigDecimal totalPrincipal, BigDecimal totalFees, int n, int scale) {
    if (n < 1) {
      throw new IllegalArgumentException("Batch item count must be >= 1, was " + n);
    }
    BigDecimal principal = totalPrincipal != null ? totalPrincipal : BigDecimal.ZERO;
    BigDecimal fees = totalFees != null ? totalFees : BigDecimal.ZERO;

    BigDecimal divisor = BigDecimal.valueOf(n);
    BigDecimal perPrincipal = principal.divide(divisor, scale, RoundingMode.DOWN);
    BigDecimal perFee = fees.divide(divisor, scale, RoundingMode.DOWN);

    List<ItemAmount> items = new ArrayList<>(n);
    BigDecimal accPrincipal = BigDecimal.ZERO;
    BigDecimal accFee = BigDecimal.ZERO;
    for (int i = 0; i < n - 1; i++) {
      items.add(new ItemAmount(perPrincipal, perFee));
      accPrincipal = accPrincipal.add(perPrincipal);
      accFee = accFee.add(perFee);
    }
    // Last item absorbs the rounding remainder so the slices sum back to the totals exactly.
    items.add(
        new ItemAmount(
            principal.subtract(accPrincipal).setScale(scale, RoundingMode.HALF_UP),
            fees.subtract(accFee).setScale(scale, RoundingMode.HALF_UP)));
    return List.copyOf(items);
  }
}
