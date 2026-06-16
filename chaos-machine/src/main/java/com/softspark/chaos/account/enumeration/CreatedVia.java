package com.softspark.chaos.account.enumeration;

/**
 * How a virtual account was created.
 */
public enum CreatedVia {
  BOOTSTRAP,
  API,
  KAFKA,
  /** Account was provisioned via the ledger HTTP API during Phase 025 bootstrap. */
  LEDGER_PROVISIONED
}
