package com.softspark.chaos.organization.dto;

import io.soabase.recordbuilder.core.RecordBuilder;
import jakarta.validation.constraints.NotBlank;

/**
 * Request record for updating an organization type.
 *
 * @param name the organization type name (unique, case-insensitive)
 */
@RecordBuilder
public record UpdateOrganizationTypeRequest(@NotBlank(message = "Name is required") String name) {}
