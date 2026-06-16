package com.softspark.chaos.batch.enumeration;

/**
 * Status of a single row in a batch run.
 */
public enum BatchRowStatus {
  PENDING,
  PUBLISHED,
  FAILED,
  INVALID
}
