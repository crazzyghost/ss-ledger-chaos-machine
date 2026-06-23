package com.softspark.chaos.flow.dto;

/**
 * A client-side prefill rule: how a field's value is inferred from the virtual account(s) the
 * operator selects. Applied in the browser off the selected {@code VirtualAccountResponse}
 * (which carries {@code ownershipType}, {@code organizationId}, {@code currency}); the catalog only
 * declares the rule.
 */
public enum InferenceRule {
  /** No inference. */
  NONE,
  /** Organization id of the selected source VA. */
  ORG_FROM_SOURCE_VA,
  /** Organization id of the selected destination VA. */
  ORG_FROM_DEST_VA,
  /** Currency of the selected source VA. */
  CURRENCY_FROM_SOURCE_VA,
  /** Organization id of the source VA, only when its ownership is {@code ORGANIZATION}. */
  TENANT_FROM_SOURCE_VA
}
