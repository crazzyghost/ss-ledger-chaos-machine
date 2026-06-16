package com.softspark.chaos.account.bootstrap;

import io.soabase.recordbuilder.core.RecordBuilder;
import java.util.List;

/**
 * Summary of a chart-of-accounts bootstrap run.
 *
 * @param provisioned number of roles successfully provisioned in this run
 * @param pending     number of roles still awaiting provisioning
 * @param failed      number of roles in the FAILED state
 * @param errors      per-role error messages encountered during this run
 */
@RecordBuilder
public record BootstrapResult(int provisioned, int pending, int failed, List<String> errors) {}
