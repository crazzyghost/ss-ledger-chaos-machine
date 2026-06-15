package com.softspark.chaos.base;

import io.soabase.recordbuilder.core.RecordBuilder;

/**
 * Field-level error description.
 * <p>
 * Used within {@link ApiError} to describe validation failures on specific request fields.
 *
 * @param field   the name of the field that failed validation
 * @param message a human-readable error message for this field
 */
@RecordBuilder
public record ErrorDescription(String field, String message) {}
