package com.softspark.chaos.flow.chaos;

/**
 * Execution shape for an {@link NTimesOptions} run.
 *
 * <p>{@code SYNC} runs in-line on the request thread (sequential, capped); {@code ASYNC} offloads to
 * the run-tracked batch runner and returns a run handle.
 */
public enum ExecutionMode {
  /** Run in-line on the request thread, sequentially, returning an aggregate summary. */
  SYNC,
  /** Run as a tracked background run, returning a run handle to poll. */
  ASYNC
}
