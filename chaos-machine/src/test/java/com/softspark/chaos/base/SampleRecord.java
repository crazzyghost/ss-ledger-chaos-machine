package com.softspark.chaos.base;

import io.soabase.recordbuilder.core.RecordBuilder;

/**
 * Sample record for testing @RecordBuilder annotation processing.
 */
@RecordBuilder
public record SampleRecord(String name, int value) {}
