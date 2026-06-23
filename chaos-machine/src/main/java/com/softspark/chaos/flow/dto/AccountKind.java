package com.softspark.chaos.flow.dto;

/**
 * For {@link FieldKind#VA_REF} fields, which kind of virtual account the picker is scoped to. Maps
 * to the VA registry's ownership type.
 */
public enum AccountKind {
  /** An organization-owned VA (operator picks one). */
  ORGANIZATION,
  /** A system/chart-of-accounts VA (defaults from the configured slot, overridable). */
  SYSTEM
}
