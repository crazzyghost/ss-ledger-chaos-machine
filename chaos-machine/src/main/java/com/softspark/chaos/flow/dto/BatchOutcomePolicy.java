package com.softspark.chaos.flow.dto;

import org.springframework.lang.Nullable;

/**
 * The outcome policy for an automatic batch-disbursement run: how each of the N items resolves
 * (completed vs failed). See ADR-022 §4.
 *
 * <ul>
 *   <li>{@link Mode#ALL_PASS}/{@link Mode#ALL_FAIL} — every item completes/fails;
 *   <li>{@link Mode#COUNT} — exactly {@code passCount} items complete (the first {@code passCount} by
 *       sequence), the rest fail; {@code 0 <= passCount <= N};
 *   <li>{@link Mode#RANDOM} — per-item outcome decided deterministically by the
 *       {@code OutcomeDecider} (seed + index); when {@code passCount} is present it is honoured as an
 *       exact target (the {@code passCount} highest-ranked items pass).
 * </ul>
 *
 * @param mode the outcome mode
 * @param passCount the number of items that should pass (required for {@code COUNT}; an optional
 *     target for {@code RANDOM}); validated into {@code [0, N]}
 * @param seed an optional explicit seed for {@code RANDOM} (else derived from the run id, so runs are
 *     resume-safe)
 */
public record BatchOutcomePolicy(Mode mode, @Nullable Integer passCount, @Nullable Long seed) {

  /** The outcome modes. */
  public enum Mode {
    /** Every item completes. */
    ALL_PASS,
    /** Every item fails. */
    ALL_FAIL,
    /** Exactly {@code passCount} items complete (the first by sequence); the rest fail. */
    COUNT,
    /** Per-item outcome decided deterministically (seed + index). */
    RANDOM
  }
}
