package com.softspark.chaos.flow.dto;

/**
 * The UI/semantic kind of a flow field, used by the Single Flow Run form to choose a control and
 * seeding behaviour.
 */
public enum FieldKind {
  /** Free text input. */
  TEXT,
  /** A UUID value; usually paired with {@link AutogenRule#UUID_V4}. */
  UUID,
  /** A decimal monetary amount. */
  AMOUNT,
  /** An ISO-8601 timestamp. */
  DATETIME,
  /** A constrained choice rendered from {@code options}. */
  SELECT,
  /** A virtual-account reference, picked from VAs filtered by {@code accountKind}. */
  VA_REF
}
