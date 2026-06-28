package com.softspark.chaos.flow;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Unit tests for {@link BatchSplit} — even split with remainder absorption sums back to the totals. */
@DisplayName("BatchSplit")
class BatchSplitTest {

  @ParameterizedTest
  @CsvSource({
    "1000.0000, 10, 1, 4",
    "1000.0000, 10, 3, 4",
    "1000.0000, 10, 7, 4",
    "100, 5, 3, 2",
    "1000.0000, 10, 50, 4",
    "1, 0, 3, 4",
    "9999.9999, 33.3333, 11, 4"
  })
  void should_sumSlicesBackToTotals_when_split(
      String totalPrincipal, String totalFees, int n, int scale) {
    BigDecimal p = new BigDecimal(totalPrincipal);
    BigDecimal f = new BigDecimal(totalFees);

    List<BatchSplit.ItemAmount> items = BatchSplit.even(p, f, n, scale);

    assertThat(items).hasSize(n);
    BigDecimal principalSum =
        items.stream().map(BatchSplit.ItemAmount::principal).reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal feeSum =
        items.stream().map(BatchSplit.ItemAmount::fee).reduce(BigDecimal.ZERO, BigDecimal::add);

    assertThat(principalSum).isEqualByComparingTo(p);
    assertThat(feeSum).isEqualByComparingTo(f);
    // Σ (principal + fee) == total_amount
    assertThat(principalSum.add(feeSum)).isEqualByComparingTo(p.add(f));
  }

  @ParameterizedTest
  @CsvSource({"1000.0000, 10, 3, 4", "10, 1, 7, 4"})
  void should_absorbRemainderInLastItem_when_split(
      String totalPrincipal, String totalFees, int n, int scale) {
    BigDecimal p = new BigDecimal(totalPrincipal);
    BigDecimal f = new BigDecimal(totalFees);

    List<BatchSplit.ItemAmount> items = BatchSplit.even(p, f, n, scale);

    // All non-last items are equal; the last carries the remainder (>= the others).
    BigDecimal firstPrincipal = items.get(0).principal();
    for (int i = 0; i < n - 1; i++) {
      assertThat(items.get(i).principal()).isEqualByComparingTo(firstPrincipal);
    }
    assertThat(items.get(n - 1).principal()).isGreaterThanOrEqualTo(firstPrincipal);
  }
}
